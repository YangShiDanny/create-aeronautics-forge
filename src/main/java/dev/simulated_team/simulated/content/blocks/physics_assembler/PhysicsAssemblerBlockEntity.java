package dev.simulated_team.simulated.content.blocks.physics_assembler;

import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.IDisplayAssemblyExceptions;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.config.server.blocks.SimAssembly;
import dev.simulated_team.simulated.content.blocks.behaviour.HoldTipBehaviour;
import dev.simulated_team.simulated.content.blocks.physics_assembler.assembly_preventer.DisassemblyPrevention;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler;
import dev.simulated_team.simulated.data.SimLang;
import dev.simulated_team.simulated.mixin_interface.assembly_preventer.PrimaryAssemblerExtension;
import dev.simulated_team.simulated.network.packets.physics_assembler.PhysicsAssemblerFailedPacket;
import dev.simulated_team.simulated.network.packets.physics_assembler.PhysicsAssemblerFlickAndHoldLeverPacket;
import dev.simulated_team.simulated.service.SimConfigService;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import dev.simulated_team.simulated.util.SimMathUtils;
import dev.simulated_team.simulated.util.assembly.SimAssemblyException;
import foundry.veil.api.network.VeilPacketManager;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import java.util.List;
import dev.simulated_team.simulated.network.SimPacketManager;

public class PhysicsAssemblerBlockEntity extends SmartBlockEntity implements IDisplayAssemblyExceptions, BlockEntitySubLevelActor {
    private static final float FLICKED_ANGLE_DEGREES = 45.0f;
    private static final double LEVER_CHASE_SPEED = 0.75f;

    private static final double LINEAR_STIFFNESS = 1000.0;
    private static final double LINEAR_DAMPING = 50.0;

    private static final double ANGULAR_STIFFNESS = 13000.0;
    private static final double ANGULAR_DAMPING = 1000.0;
    private static final MutableComponent ASSEMBLE_TIP = SimLang.translate("gui.hold_tip.hold_to_assemble").component();
    private static final MutableComponent DISASSEMBLE_TIP = SimLang.translate("gui.hold_tip.hold_to_disassemble").component();

    protected AssemblyException lastException;
    protected boolean primaryAssembler;
    protected LerpedFloat visualAngle = LerpedFloat.linear();

    /**
     * When the player lets go of the lever when assembling / disassembling, we want to hold the lever in place
     * until we either receive an assembly failure
     */
    protected boolean holdingLever = false;
    private boolean leverInitialized = false;

    private boolean disassembling = false;
    private int disassemblingTicks = 0;
    private int disassemblyReadyTicks = 0;
    private int disassemblyAngle = 0;
    private Quaterniondc disassemblyOrientation;

    private boolean controlledByPlayer = false;
    private float playerAngle = 0.0f;

    
    private FreeConstraintHandle alignmentConstraint;
    private HoldTipBehaviour holdTipBehaviour;

    public PhysicsAssemblerBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(final List<BlockEntityBehaviour> behaviours) {
        behaviours.add(this.holdTipBehaviour = new HoldTipBehaviour(this, ASSEMBLE_TIP));
    }

    @Override
    public void initialize() {
        super.initialize();

        if (this.primaryAssembler) {
            this.setParent(this.getLevel());
        }

        if (!this.isVirtual()) {
            this.initializeLeverPosition();
            this.holdTipBehaviour.setHoverTip(this.getSubLevel() != null ? DISASSEMBLE_TIP : ASSEMBLE_TIP);
        }
    }

    protected void initializeLeverPosition() {
        if (!this.leverInitialized) {
            this.clientFlickLeverTo(this.getSubLevel() != null);
            this.jerkLever();
            this.leverInitialized = true;
        }
    }

    private SubLevel getSubLevel() {
        return this.findOwningSubLevel();
    }

    // [第19轮] 双端通用的「所属刚体」查找：先走 getContaining(源版路径)；
    // 移植架构下(所有子关卡固定挂 2048 万格合成 plot、与刚体主世界位置脱钩)它恒 null，
    // 则退回「宽召回 expand(2.0) + 逆变换到存储坐标精确校验(3x3x3 容差)」。
    // 客户端也必须用它判定「是否已物理化」：GUI 此前用裸 getContaining -> 客户端恒 null ->
    // 拉杆每次都从 0 开始、拉满就发一次切换包 -> 已物理化的结构被再次切换(20:11 日志
    // release 9 次全 inPlot=false 实锤，即「物理化的方块自己又去物理化/到处飘」)。
    public SubLevel findOwningSubLevel() {
        final SubLevel direct = Sable.HELPER.getContaining(this);
        if (direct != null) {
            return direct;
        }
        final Level level = this.getLevel();
        if (level == null) {
            return null;
        }
        final Block myBlock = this.getBlockState().getBlock();
        final BlockPos myPos = this.getBlockPos();
        final Vec3 center = myPos.getCenter();
        final BoundingBox3d queryBounds = new BoundingBox3d(myPos).expand(2.0);
        for (final SubLevel candidate : Sable.HELPER.getAllIntersecting(level, queryBounds)) {
            final Vec3 inv = candidate.logicalPose().transformPositionInverse(center);
            final BlockPos base = BlockPos.containing(inv.x, inv.y, inv.z);
            // [第21轮·去邻居误命中] 扫描存储坐标附近含本方块的格子，但只认「该格子正向变换回主世界取整正好 = myPos」的那个。
            // 两台相邻组装器是同种方块：邻居的存储格子正向变换会落回它自己的位置(≠myPos) -> 被排除，
            // 不再把邻居的刚体误当成自己的(上轮 3x3x3 任意命中同种方块 -> 按一下邻居的拉杆就把已物理化的那台拆了)。
            // 正向变换与镜像方块放置同路径，身份判定精确(不像逆变换双重取整会漂 1 格)；3x3x3 仅为吸收刚体亚格漂移的召回容差。
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        final BlockPos cell = base.offset(dx, dy, dz);
                        if (!level.getBlockState(cell).is(myBlock)) {
                            continue;
                        }
                        final BlockPos back = BlockPos.containing(candidate.logicalPose().transformPosition(cell.getCenter()));
                        if (back.equals(myPos)) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.disassembling) {
            this.tickDisassembling();
        }

        if (this.holdingLever) {
            this.visualAngle.setValue(this.visualAngle.getValue());
        } else {
            if (this.controlledByPlayer) {
                this.visualAngle.setValue(this.visualAngle.getValue());
                this.visualAngle.setValueNoUpdate(this.playerAngle);
            } else {
                this.visualAngle.tickChaser();
            }
        }
    }

    private void tickDisassembling() {
        this.disassemblingTicks++;

        final SimAssembly config = SimConfigService.INSTANCE.server().assembly;
        if (this.disassemblingTicks >= config.maxDisassemblyTicks.get() * 5) {
            this.assemblyFailed(SimAssemblyException.couldNotAlign());
            this.stopDisassembling();
            return;
        }

        final SubLevel subLevel = this.getSubLevel();
        if (subLevel instanceof ServerSubLevel) {
            final Pose3d pose = subLevel.logicalPose();
            final double angle = pose.orientation().div(this.disassemblyOrientation, new Quaterniond()).angle();

            final Vector3d current = pose.transformPosition(new Vector3d(pose.rotationPoint()).floor().add(0.5, 0.5, 0.5));
            final Vector3d goal = current.floor(new Vector3d()).add(0.5, 0.5, 0.5);
            final Vector3d localGoal = this.disassemblyOrientation.transformInverse(goal, new Vector3d());

            this.alignmentConstraint.setMotor(ConstraintJointAxis.LINEAR_X, localGoal.x, LINEAR_STIFFNESS, LINEAR_DAMPING, false, 0.0);
            this.alignmentConstraint.setMotor(ConstraintJointAxis.LINEAR_Y, localGoal.y, LINEAR_STIFFNESS, LINEAR_DAMPING, false, 0.0);
            this.alignmentConstraint.setMotor(ConstraintJointAxis.LINEAR_Z, localGoal.z, LINEAR_STIFFNESS, LINEAR_DAMPING, false, 0.0);

            if (Math.toDegrees(Math.abs(angle)) <= config.disassemblyDegreeTolerance.get() && current.distance(goal) < 0.2) {
                this.disassemblyReadyTicks++;
            } else {
                this.disassemblyReadyTicks = 0;
            }

            if (this.disassemblyReadyTicks > 5) {
                this.placeIntoWorld();
            }
        }
    }

    private void placeIntoWorld() {
        final SubLevel subLevel = this.getSubLevel();
        assert subLevel != null;

        try {
            this.throwDisassemblyExceptions((ServerSubLevel) subLevel);
        } catch (final AssemblyException e) {
            // [1.20.1 port 调试] 记录拆解被哪个校验拦截（tooFast / tooFarFromGround / outOfWorld）
            this.assemblyFailed(e);
            this.stopDisassembling();
            return;
        }

        // [第18轮·真凶] 源版 BE 本体住在子关卡存储区(this.getBlockPos()=存储坐标 2048万格)，anchor/goal 成立；
        // 移植版 this 是主世界镜像 BE(小值坐标)，把镜像坐标喂给 transformPosition/AssemblyTransform 的 anchor，
        // goal 被算到约 -2048 万格荒外 -> 方块从刚体删除后被搬到天边 = 「拆解后消失」(19:28 日志：删除成功但不落地)。
        // 修复：先把镜像中心逆变换回存储坐标得到真 anchor，再用 anchor 算 goal(该方块当前真实主世界位置)。
        final BlockPos anchor = this.findStorageAnchor(subLevel);
        final BlockPos goal = BlockPos.containing(subLevel.logicalPose().transformPosition(Vec3.atCenterOf(anchor)));
        final Rotation rotation = SimAssemblyHelper.rotationFrom90DegRots(this.disassemblyAngle);
        SimAssemblyHelper.disassembleSubLevel(this.getLevel(), subLevel, anchor, goal, rotation, true);
        this.stopDisassembling();
    }

    // [第18轮] 把主世界镜像坐标逆变换回子关卡存储坐标；含正负1邻域容差(吸收刚体漂移的亚格误差)。
    private BlockPos findStorageAnchor(final SubLevel subLevel) {
        // [第19轮修复] BE 本体可能就住在子关卡存储区（2048万格），此时 getBlockPos() 已是存储坐标，
        // 直接返回；只有主世界镜像 BE（小值坐标）才需要逆变换回存储坐标。
        // 上次「无条件逆变换」把存储坐标又逆变换一次 → anchor 变成 4100 万格垃圾坐标 → goal 全错 → 方块搬到 y=2177 越界丢失。
        if (Sable.HELPER.getContaining(this.getLevel(), this.getBlockPos()) == subLevel) {
            return this.getBlockPos();
        }
        final Vec3 inv = subLevel.logicalPose().transformPositionInverse(this.getBlockPos().getCenter());
        final BlockPos base = BlockPos.containing(inv.x, inv.y, inv.z);
        final Block myBlock = this.getBlockState().getBlock();
        if (this.getLevel().getBlockState(base).is(myBlock)) {
            return base;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    final BlockPos p = base.offset(dx, dy, dz);
                    if (this.getLevel().getBlockState(p).is(myBlock)) {
                        return p;
                    }
                }
            }
        }
        return base;
    }

    private void throwDisassemblyExceptions(final ServerSubLevel subLevel) throws AssemblyException {
        final BoundingBox3dc bounds = subLevel.boundingBox();
        if (bounds.maxY() > this.getLevel().getMaxBuildHeight()
                || bounds.minY() < this.getLevel().getMinBuildHeight()) {
            throw SimAssemblyException.outOfWorld();
        }

        final SimAssembly config = SimConfigService.INSTANCE.server().assembly;

        final RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        if (handle.getLinearVelocity(new Vector3d()).lengthSquared() > Mth.square(config.disassemblyMaxVelocity.getF()) ||
                handle.getAngularVelocity(new Vector3d()).lengthSquared() > Mth.square(config.disassemblyMaxAngularVelocity.getF())) {
            throw SimAssemblyException.tooFast();
        }

        final BoundingBox3i chunkBounds = new BoundingBox3i(
                (Mth.floor(bounds.minX()) >> 4) - 1,
                (Mth.floor(bounds.minY()) >> 4) - 1,
                (Mth.floor(bounds.minZ()) >> 4) - 1,
                (Mth.floor(bounds.maxX()) >> 4) + 1,
                (Mth.floor(bounds.maxY()) >> 4) + 1,
                (Mth.floor(bounds.maxZ()) >> 4) + 1
        );

        if (config.disallowMidAirDisassembly.get()) {
            boolean nearGround = false;

            scanSectionsLoop:
            for (int x = chunkBounds.minX(); x <= chunkBounds.maxX(); x++) {
                for (int z = chunkBounds.minZ(); z <= chunkBounds.maxZ(); z++) {
                    final LevelChunk chunk = this.getLevel().getChunk(x, z);

                    for (int y = chunkBounds.minY(); y <= chunkBounds.maxY(); y++) {
                        final int index = chunk.getSectionIndexFromSectionY(y);

                        if (index < 0 || index >= chunk.getSectionsCount()) {
                            continue;
                        }

                        if (!chunk.getSection(index).hasOnlyAir()) {
                            nearGround = true;
                            break scanSectionsLoop;
                        }
                    }
                }
            }

            // [1.20.1 port 调试] 记录地面检测结果，确认 plot 坐标在主世界取不到地面
            if (!nearGround) {
                throw SimAssemblyException.tooFarFromGround();
            }
        }
    }

    private void stopDisassembling() {
        if (this.alignmentConstraint != null && this.alignmentConstraint.isValid()) {
            this.alignmentConstraint.remove();
            this.alignmentConstraint = null;
        }

        this.disassemblingTicks = 0;
        this.disassembling = false;
    }

    public void setClientHoldLeverInPlace(final boolean holding) {
        this.holdingLever = holding;
    }

    public void updateControlledByPlayer(final float angle) {
        if (!this.controlledByPlayer) {
            this.controlledByPlayer = true;
        }
        this.playerAngle = angle;
    }

    public boolean stopControllingPlayer() {
        if (!this.controlledByPlayer) return false;
        this.controlledByPlayer = false;
        return true;
    }

    public void clientFlickLeverTo(final boolean flicked) {
        this.visualAngle.chase(flicked ? FLICKED_ANGLE_DEGREES : 0.0f, LEVER_CHASE_SPEED, LerpedFloat.Chaser.EXP);
    }

    public void jerkLever() {
        this.visualAngle.setValue(this.visualAngle.getChaseTarget());
        this.visualAngle.setValue(this.visualAngle.getChaseTarget());
    }

    public void assembleOrDisassemble() {
        // [第17轮] 组装/拆解入口与每帧对齐推进统一走 getSubLevel()：
        //   getContaining 是纯 plot 区块查询，携带态镜像 BE 坐标每刻随刚体漂移 -> 永远 null；
        //   getSubLevel 内部做「宽召回 expand(2.0) + 逆变换到存储坐标精确校验」，按格子命中、不靠外接盒粗判。
        final SubLevel subLevel = this.getSubLevel();

        final Level level = this.getLevel();
        assert level != null;
        try {
            dev.simulated_team.simulated.network.SimPacketManager.INSTANCE.tracking(this).sendPacket(new PhysicsAssemblerFlickAndHoldLeverPacket(this.worldPosition, subLevel == null));

            if (subLevel instanceof final ServerSubLevel serverSubLevel) {
                if (DisassemblyPrevention.checkSubLevelForPrimary(level, this.getBlockPos())) {
                    // [1.20.1 port 修复] NeoForge 1.21.1 源版在「携带态拉杆」瞬间刚体速度≈0，能通过 tooFast 校验；
                    // 但 1.20.1 移植版携带结构跟着玩家移动时持续带速，linearVelocity² > disassemblyMaxVelocity²(5.0)
                    // 触发 tooFast，永远拦住拆解，结构卡在「跟随玩家」的携带态（即你看到的「拉杆跟着玩家漂」）。
                    // 等价于源版状态：开始拆解前先把刚体速度清零，后续由对齐马达接管并把结构平稳放回世界。
                    // 该 bug 与方块种类无关（所有携带结构都带速），故对所有方块一致生效。
                    final RigidBodyHandle preHandle = RigidBodyHandle.of(serverSubLevel);
                    final Vector3d preLin = preHandle.getLinearVelocity(new Vector3d());
                    final Vector3d preAng = preHandle.getAngularVelocity(new Vector3d());
                    preHandle.addLinearAndAngularVelocity(preLin.negate(), preAng.negate());

                    this.throwDisassemblyExceptions(serverSubLevel);
                    this.startDisassembling(serverSubLevel, (ServerLevel) level, subLevel);
                    this.disassembling = true;
                }
            } else {
                this.primaryAssembler = true;

                final BlockPos toAssemble = this.getBlockPos().relative(PhysicsAssemblerBlock.getStickyFacing(this.getBlockState()));
                SimAssemblyHelper.assembleFromSingleBlock(level, this.getBlockPos(), toAssemble, true, true);

                this.lastException = null;
                this.sendData();
            }
        } catch (final AssemblyException e) {
            if (!(subLevel instanceof ServerSubLevel)) {
                this.primaryAssembler = false;
            }

            this.assemblyFailed(e);
        }
    }

    private void assemblyFailed(final AssemblyException exception) {
        this.lastException = exception;
        dev.simulated_team.simulated.network.SimPacketManager.INSTANCE.tracking(this).sendPacket(new PhysicsAssemblerFailedPacket(this.worldPosition));
        this.sendData();
    }

    private void startDisassembling(final ServerSubLevel serverSubLevel, final ServerLevel level, final SubLevel subLevel) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        final PhysicsPipeline pipeline = container.physicsSystem().getPipeline();

        // Setup constraint
        final MassData massTracker = serverSubLevel.getMassTracker();

        final double closestYRotation = SimMathUtils.getClosestYaw(subLevel.logicalPose().orientation());
        final double ninety = Math.PI / 2.0;
        final int turns = -(Mth.floor(closestYRotation / ninety + 0.5));
        this.disassemblyAngle = turns;

        final FreeConstraintConfiguration config = new FreeConstraintConfiguration(new Vector3d(),
                new Vector3d(massTracker.getCenterOfMass()).floor().add(0.5, 0.5, 0.5),
                this.disassemblyOrientation = new Quaterniond().rotateY(turns * ninety));
        this.alignmentConstraint = pipeline.addConstraint(null, serverSubLevel, config);

        this.alignmentConstraint.setMotor(ConstraintJointAxis.ANGULAR_X, 0.0, ANGULAR_STIFFNESS, ANGULAR_DAMPING, false, 0.0);
        this.alignmentConstraint.setMotor(ConstraintJointAxis.ANGULAR_Z, 0.0, ANGULAR_STIFFNESS, ANGULAR_DAMPING, false, 0.0);
        this.alignmentConstraint.setMotor(ConstraintJointAxis.ANGULAR_Y, 0.0, ANGULAR_STIFFNESS, ANGULAR_DAMPING, false, 0.0);

        this.alignmentConstraint.setMotor(ConstraintJointAxis.LINEAR_X, 0.0, 0.000001, LINEAR_DAMPING, false, 0.0);
        this.alignmentConstraint.setMotor(ConstraintJointAxis.LINEAR_Y, 0.0, 0.000001, LINEAR_DAMPING, false, 0.0);
        this.alignmentConstraint.setMotor(ConstraintJointAxis.LINEAR_Z, 0.0, 0.000001, LINEAR_DAMPING, false, 0.0);

        this.disassembling = true;
        this.disassemblingTicks = 0;

        // Remove any locks on the sub-level
        PhysicsStaffServerHandler.get(level).removeLock(serverSubLevel);
    }

    @Override
    public void remove() {
        //if we're the primary assembler, set the parent's primary assembler to null to ensure any assembler can disassemble
        if (this.primaryAssembler) {
            if (!this.getLevel().isClientSide) {
                final SubLevel subLevel = this.getSubLevel();
                if (subLevel instanceof final ServerSubLevel ssb) {
                    ((PrimaryAssemblerExtension) ssb).simulated$setPrimaryAssembler(null);
                    // [1.20.1 port 回退] 源版（NeoForge 1.21.1）remove() 不做归还：破坏组装器后
                    // 剩余结构继续保持物理化漂浮，归还只由长按拉杆 placeIntoWorld 触发。
                    // 之前在此处加的 disassembleSubLevel 调用是误判（且区块卸载时也会误触），已删除。
                }
            }
        }

        this.stopDisassembling();

        super.remove();
    }

    public void write(final CompoundTag compound, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.write(compound, clientPacket);

        AssemblyException.write(compound, this.lastException);
        compound.putBoolean("IsPrimary", this.primaryAssembler);
    }

    protected void read(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.read(tag, clientPacket);

        this.lastException = AssemblyException.read(tag);
        this.primaryAssembler = tag.getBoolean("IsPrimary");
    }

    @Override
    public AssemblyException getLastAssemblyException() {
        return this.lastException;
    }

    public boolean isPrimaryAssembler() {
        return this.primaryAssembler;
    }

    protected void setParent(final Level level) {
        this.lastException = null;

        final SubLevel subLevel = Sable.HELPER.getContaining(level, this.getBlockPos());
        if (!level.isClientSide && this.primaryAssembler && subLevel instanceof ServerSubLevel) {
            final PrimaryAssemblerExtension duck = (PrimaryAssemblerExtension) subLevel;
            if (duck.simulated$getPrimaryAssembler() == null) {
                duck.simulated$setPrimaryAssembler(this.getBlockPos());
            }
        } else {
            // we disassembled, so no assembler is primary
            this.primaryAssembler = false;
        }
    }

    public float getClientAngle(final float partialTicks) {
        return this.visualAngle.getValue(partialTicks);
    }
}
