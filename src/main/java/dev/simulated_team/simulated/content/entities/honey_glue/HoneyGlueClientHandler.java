package dev.simulated_team.simulated.content.entities.honey_glue;

import com.simibubi.create.AllKeys;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.foundation.utility.RaycastHelper;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.data.SimLang;
import dev.simulated_team.simulated.index.SimEntityTypes;
import dev.simulated_team.simulated.index.SimItems;
import dev.simulated_team.simulated.index.SimSoundEvents;
import dev.simulated_team.simulated.index.SimSpecialTextures;
import dev.simulated_team.simulated.mixin.aabb.AABBMixin;
import dev.simulated_team.simulated.network.packets.honey_glue.HoneyGlueChangeBoundsPacket;
import dev.simulated_team.simulated.network.packets.honey_glue.HoneyGlueSpawnPacket;
import dev.simulated_team.simulated.service.SimConfigService;
import dev.simulated_team.simulated.util.SimColors;
import dev.simulated_team.simulated.util.SimDistUtil;
import dev.simulated_team.simulated.util.click_interactions.InteractCallback;
import foundry.veil.api.network.VeilPacketManager;
import dev.simulated_team.simulated.libs.Pair;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import dev.simulated_team.simulated.libs.minecraft.core.component.DataComponentMap;
import dev.simulated_team.simulated.libs.minecraft.core.component.DataComponentType;
import dev.simulated_team.simulated.libs.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Optional;
import net.minecraftforge.common.ForgeMod;
import dev.simulated_team.simulated.network.SimPacketManager;

public class HoneyGlueClientHandler implements InteractCallback {
    private State currentState = State.UNBOUND;
    private BlockPos selectedPos;

    private HoneyGlueEntity hoveredGlue;
    private Direction hoveredFace;

    /**
     * Called to select a position, either beginning or finishing a honey glue placement
     * @return true if successful
     */
    public boolean selectPos(final BlockPos pos, final Player player, final ItemStack honeyGlueStack) {
        if (this.currentState == State.UNBOUND) {
            this.selectedPos = pos;
            this.currentState = State.BINDING;
            SimSoundEvents.HONEY_ADDED.playAt(player.level(), this.selectedPos, 0.5F, 0.95F, false);
            player.level().playSound(player, this.selectedPos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.75f, 1);
        } else {
            final boolean valid = this.checkBBValidity(player, honeyGlueStack, pos, false);
            if (!valid) {
                return false;
            }
            dev.simulated_team.simulated.network.SimPacketManager.INSTANCE.server().sendPacket(new HoneyGlueSpawnPacket(this.selectedPos, pos));
            SimSoundEvents.HONEY_ADDED.playAt(player.level(), pos, 0.5F, 0.95F, false);
            player.level().playSound(player, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.75f, 1);
            this.selectedPos = null;
            this.currentState = State.UNBOUND;
        }

        return true;
    }

    @Override
    public Result onUse(final int modifiers, final int action, final KeyMapping rightKey) {
        if (action == GLFW.GLFW_PRESS) {
            final Player player = SimDistUtil.getClientPlayer();
            final InteractionHand hand = this.getHoneyGlueHand(player);
            if (hand == null) {
                return Result.empty();
            }

            if (player.isShiftKeyDown()) {
                this.clearAndSwing(player);
                return new Result(true);
            }

            final BlockHitResult bhr = this.getHitResult();
            if (AllKeys.altDown() && bhr.getType() == HitResult.Type.MISS) {
                final boolean success = this.selectPos(bhr.getBlockPos(), player, player.getItemInHand(hand));
                if (success) {
                    player.swing(InteractionHand.MAIN_HAND);
                    return new Result(true);
                } else {
                    return Result.empty();
                }
            }
        }

        return InteractCallback.super.onUse(modifiers, action, rightKey);
    }

    @Override
    public Result onAttack(final int modifiers, final int action, final KeyMapping leftKey) {
        if (action == GLFW.GLFW_PRESS) {
            final Player player = SimDistUtil.getClientPlayer();
            if (this.getHoneyGlueHand(player) == null) {
                return Result.empty();
            }

            if (this.hoveredGlue != null) {
                dev.simulated_team.simulated.network.SimPacketManager.INSTANCE.server().sendPacket(new HoneyGlueChangeBoundsPacket(new AABB(0, 0, 0, 0, 0, 0), this.hoveredGlue.getUUID()));
                this.clearAndSwing(player);
                return new Result(true);
            }
        }

        return InteractCallback.super.onAttack(modifiers, action, leftKey);
    }

    @Override
    public Result onScroll(final double deltaX, double deltaY) {
        final Player player = SimDistUtil.getClientPlayer();

        if (player == null)
            return Result.empty();

        final InteractionHand hand = this.getHoneyGlueHand(player);

        if (hand == null || !AllKeys.ctrlDown()) {
            return Result.empty();
        } else if (this.currentState == State.UNBOUND && this.hoveredGlue != null) {
            this.hoveredGlue.updateClientBounds();
            final AABB bb = this.hoveredGlue.getBoundingBox();

	        final ClientSubLevel clientSublevel = Sable.HELPER.getContainingClient(bb.getCenter());
	        Vec3 eyePos = player.getEyePosition();
	        if (clientSublevel != null) {
		        eyePos = clientSublevel.renderPose().transformPositionInverse(eyePos);
			}

	        if (bb.contains(eyePos)) {
                deltaY *= -1;
            }

            final AABB newBounds = this.extendHoneyBB(bb, (int) deltaY);
            final Pair<Boolean, String> pair = HoneyGlueMaxSizing.checkBounds(newBounds);

            if (pair.getFirst()) {
                this.hoveredGlue.setBounds(newBounds);
                dev.simulated_team.simulated.network.SimPacketManager.INSTANCE.server().sendPacket(new HoneyGlueChangeBoundsPacket(newBounds, this.hoveredGlue.getUUID()));
            } else {
                SimLang.text(pair.getSecond() + getDimensionalText(bb))
                        .color(SimColors.NUH_UH_RED)
                        .sendStatus(player);
            }
        }

        return new Result(true);
    }

    @Override
    public void clientTick(final Level level, final LocalPlayer player) {
        final InteractionHand hand = this.getHoneyGlueHand(player);
        if (hand == null) {
            this.hoveredGlue = null;
            this.selectedPos = null;
            this.currentState = State.UNBOUND;
            return;
        }

        final BlockHitResult bhr = this.getHitResult();

        this.renderSelection(bhr, player.isShiftKeyDown() ? SimColors.DISCARDABLE_ORANGE : SimColors.ACTIVE_YELLOW);
        this.updateHovered();
        this.renderHoneyGlue();
        this.checkBBValidity(player, player.getItemInHand(hand), bhr.getBlockPos(), AllKeys.altDown() || bhr.getType() != HitResult.Type.MISS);
    }

    private void renderSelection(final BlockHitResult bhr, final int color) {
        // [Port] 对齐原版 NeoForge 1.21.1 逻辑：不做坐标变换，直接传 AABB 给 Outliner
        // BlockGetterMixin.clip 只在射线端点位于子关卡内时才变换坐标；
        // 玩家在主世界时返回纯世界坐标，在子关卡内时 Outliner/Sable 系统自行处理
        if (AllKeys.altDown() && !AllKeys.shiftDown() && this.currentState == State.UNBOUND) {
            Outliner.getInstance().showAABB("HoneyGlue", new AABB(bhr.getBlockPos()))
                    .colored(color)
                    .withFaceTexture(SimSpecialTextures.HONEY_GLUE)
                    .disableLineNormals()
                    .lineWidth(1 / 16f);
        } else if ((AllKeys.altDown() || bhr.getType() != HitResult.Type.MISS) && this.currentState == State.BINDING) {
            // [Port] 原版 containment：用 BlockPos 查子关卡（非 eyePosition）
            if (Sable.HELPER.getContainingClient(bhr.getBlockPos()) != Sable.HELPER.getContainingClient(this.selectedPos)) {
                return;
            }

            // [Port] 1.20.1 无 AABB.encapsulatingFullBlocks(pos,pos)，用两个整块 AABB 取并集等价替代
            Outliner.getInstance().showAABB("HoneyGlue", new AABB(bhr.getBlockPos()).minmax(new AABB(this.selectedPos)))
                    .colored(color)
                    .withFaceTexture(SimSpecialTextures.HONEY_GLUE)
                    .disableLineNormals()
                    .lineWidth(1 / 16f);
        }
    }

    /**
     * 将子关卡本地坐标的 AABB 变换到世界坐标。
     * 用于 renderHoneyGlue 中实体 AABB 的渲染（实体 getBoundingBox() 返回本地坐标）。
     * renderSelection 不需要此方法（对齐原版逻辑，直接传 BlockPos/AABB 给 Outliner）。
     */
    private static AABB transformAabbToWorld(final AABB local, @Nullable final ClientSubLevel subLevel) {
        if (subLevel == null) {
            return local;
        }
        final Pose3dc pose = subLevel.renderPose();
        // 取 8 个角点，逐个变换到世界坐标，再重建轴对齐包围盒
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE, maxZ = Double.MIN_VALUE;
        for (int x = 0; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = 0; z <= 1; z++) {
                    final Vec3 corner = new Vec3(
                            x == 0 ? local.minX : local.maxX,
                            y == 0 ? local.minY : local.maxY,
                            z == 0 ? local.minZ : local.maxZ
                    );
                    final Vec3 world = pose.transformPosition(corner);
                    minX = java.lang.Math.min(minX, world.x); minY = java.lang.Math.min(minY, world.y); minZ = java.lang.Math.min(minZ, world.z);
                    maxX = java.lang.Math.max(maxX, world.x); maxY = java.lang.Math.max(maxY, world.y); maxZ = java.lang.Math.max(maxZ, world.z);
                }
            }
        }
        final AABB result = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        return result;
    }

    public void updateHovered() {
        final Player player = SimDistUtil.getClientPlayer();
        final Vec3 baseOrigin = player.getEyePosition();
        final Vec3 baseTarget = RaycastHelper.getTraceTarget(player, player.getAttributeValue(ForgeMod.BLOCK_REACH.get()) * 5, baseOrigin);

        HoneyGlueEntity closestGlue = null;
        double distance = Double.MAX_VALUE;
        Direction closestDir = null;

        for (final HoneyGlueEntity entity : getHoneyGlue(player)) {
            final AABB toClip = entity.getBoundingBox();

            final SubLevel subLevel = Sable.HELPER.getContainingClient(toClip.getCenter());
            Vec3 subLevelOrigin = baseOrigin;
            Vec3 subLevelTarget = baseTarget;
            if (subLevel != null) {
                // 必须用 renderPose（与 GameRendererMixin.push 的 renderPose 一致），
                // 不能用 logicalPose（不含翻转→射线方向错误→总是命中底面）
                final Pose3dc pose = (subLevel instanceof ClientSubLevel cs) ? cs.renderPose() : subLevel.logicalPose();
                subLevelOrigin = pose.transformPositionInverse(baseOrigin);
                subLevelTarget = pose.transformPositionInverse(baseTarget);
            }

            final Optional<Vec3> clip;
            final boolean contains = toClip.contains(subLevelOrigin);
            if (contains) {
                clip = toClip.clip(subLevelTarget, subLevelOrigin);
            } else {
                clip = toClip.clip(subLevelOrigin, subLevelTarget);
            }

            if (clip.isPresent()) {
                final double hitDist = clip.get().distanceToSqr(subLevelOrigin);
                if (hitDist < distance) {
                    distance = hitDist;
                    closestGlue = entity;
                    closestDir = this.getDirectionFromAABBClip(toClip, subLevelOrigin, subLevelTarget, contains);
                }
            }
        }

        this.hoveredFace = closestDir;
        this.hoveredGlue = closestGlue;
    }

    private void renderHoneyGlue() {
        if (this.currentState == State.UNBOUND) {
            final Player player = SimDistUtil.getClientPlayer();
            for (final HoneyGlueEntity entity : getHoneyGlue(player)) {
                if (entity == this.hoveredGlue) {
                    continue;
                }

                Outliner.getInstance().showAABB("HoneyGluePassive" + entity, entity.getBoundingBox())
                        .colored(SimColors.PERCHANCE_ORANGE)
                        .disableLineNormals()
                        .lineWidth(1 / 64f);
            }

            if (this.hoveredGlue != null) {
                Outliner.getInstance().chaseAABB("HoneyGlueActive" + this.hoveredGlue.getId(), this.hoveredGlue.getBoundingBox())
                        .colored(SimColors.ACTIVE_YELLOW)
                        .withFaceTexture(SimSpecialTextures.HONEY_GLUE)
                        .highlightFace(this.hoveredFace)
                        .disableLineNormals()
                        .lineWidth(1 / 16f);
            }
        }
    }

    private boolean checkBBValidity(final Player player, final ItemStack honeyGlueStack, final BlockPos hoveredPos, final boolean showText) {
        int color = SimColors.ACTIVE_YELLOW;
        if (this.currentState == State.BINDING) {
            String key = "super_glue.click_to_confirm";

            final DataComponentMap components = DataComponentType.componentsOf(honeyGlueStack);
            final AABB bb = new AABB(this.selectedPos, hoveredPos);
            boolean showDimensions = true;

            if (HoneyGlueMaxSizing.checkBBMax(bb)) {
                key = "super_glue.too_far";
                color = SimColors.DISCARDABLE_ORANGE;
            } else {
                if (!components.has(DataComponents.MAX_DAMAGE) && honeyGlueStack.getMaxDamage() <= 0) {
                    key = "super_glue.not_enough";
                    showDimensions = false;
                    color = SimColors.DISCARDABLE_ORANGE;
                } else if (player.isShiftKeyDown()) {
                    key = "super_glue.click_to_discard";
                    showDimensions = false;
                    color = SimColors.DISCARDABLE_ORANGE;
                }
            }

            if (showText) {
                final String dimensions = getDimensionalText(bb);
                CreateLang.translate(key)
                        .text(showDimensions ? dimensions : ".")
                        .color(color)
                        .sendStatus(player);
            }
        }

        return color == SimColors.ACTIVE_YELLOW;
    }

    public BlockHitResult getHitResult() {
        final Player player = SimDistUtil.getClientPlayer();
        final Level level = player.level();

        final ClipContext clipContext = new ClipContext(
                player.getEyePosition(),
                player.getEyePosition().add(player.getViewVector(SimDistUtil.getPartialTick()).scale(player.getAttributeValue(ForgeMod.BLOCK_REACH.get()))),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                null
        );

        return level.clip(clipContext);
    }

    public Direction getDirectionFromAABBClip(final AABB aabb, Vec3 origin, Vec3 end, final boolean inside) {
        if (inside) {
            final Vec3 temp = origin;
            origin = end;
            end = temp;
        }

        final double d = end.x - origin.x;
        final double e = end.y - origin.y;
        final double f = end.z - origin.z;

        return AABBMixin.invokeGetDirection(aabb, origin, new double[]{1f}, null, d, e, f);
    }

    public AABB extendHoneyBB(AABB bb, final int delta) {
        if (this.hoveredFace == null) {
            return bb;
        }

        final int x = this.hoveredFace.getStepX() * delta;
        final int y = this.hoveredFace.getStepY() * delta;
        final int z = this.hoveredFace.getStepZ() * delta;

        final Direction.AxisDirection axisDirection = this.hoveredFace.getAxisDirection();
        if (axisDirection == Direction.AxisDirection.NEGATIVE)
            bb = bb.move(-x, -y, -z);

        final double maxX = Math.max(bb.maxX - x * axisDirection.getStep(), bb.minX);
        final double maxY = Math.max(bb.maxY - y * axisDirection.getStep(), bb.minY);
        final double maxZ = Math.max(bb.maxZ - z * axisDirection.getStep(), bb.minZ);
        return new AABB(bb.minX, bb.minY, bb.minZ, maxX, maxY, maxZ);
    }

    private static  String getDimensionalText(final AABB bb) {
        return "" /*". (X: " + (int) bounds.getXsize() + ", Y: " + (int) bounds.getYsize() + ", Z: " + (int) bounds.getZsize() + ")"*/;
    }

    private static  List<HoneyGlueEntity> getHoneyGlue(final Player player) {
        return player.level().getEntities(SimEntityTypes.HONEY_GLUE.get(), player.getBoundingBox().inflate(SimConfigService.INSTANCE.server().assembly.honeyGlueRange.get()), e -> true);
    }

    
    public InteractionHand getHoneyGlueHand(final Player player) {
        return player.getItemInHand(InteractionHand.MAIN_HAND).is(SimItems.HONEY_GLUE.get()) ? InteractionHand.MAIN_HAND :
                player.getItemInHand(InteractionHand.OFF_HAND).is(SimItems.HONEY_GLUE.get()) ? InteractionHand.OFF_HAND :
                        null;
    }

    private void clearAndSwing(final Player player) {
        this.selectedPos = null;
        this.currentState = State.UNBOUND;
        player.swing(InteractionHand.MAIN_HAND);
    }

    public enum State {
        /**
         * No initial block has been selected
         */
        UNBOUND,

        /**
         * An initial block has been selected
         */
        BINDING
    }
}
