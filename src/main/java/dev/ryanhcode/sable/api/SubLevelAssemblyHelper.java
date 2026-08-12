package dev.ryanhcode.sable.api;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.index.SableTags;
import dev.ryanhcode.sable.platform.SableAssemblyPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import dev.ryanhcode.sable.sublevel.tracking_points.TrackingPoint;
import dev.ryanhcode.sable.util.BoundedBitVolume3i;
import dev.ryanhcode.sable.util.LevelAccelerator;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.*;

/**
 * Utility class for mass movement of collections of blocks between world and plot.
 */
public class SubLevelAssemblyHelper {

    /** [BUG-35] 源 plot 区块为空时的限流日志标记，避免逐方块刷屏。 */
    private static long sable$lastNullSourceChunkLog = 0L;

    /**
     * Assembles a collection of blocks into a sub-level.
     *
     * @param level  the level in which the blocks are located
     * @param anchor the block that will be placed at the center of the sub-level
     * @param blocks all blocks that will be assembled into the sub-level
     * @param bounds the bounds in which {@link TrackingPoint tracking points} and retained entities will be moved
     */
    public static ServerSubLevel assembleBlocks(final ServerLevel level, final BlockPos anchor, final Iterable<BlockPos> blocks, final BoundingBox3ic bounds) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        assert container != null;

        final SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        final SubLevel containingSubLevel = Sable.HELPER.getContaining(level, anchor);
        final Pose3d pose = new Pose3d();

        pose.position().set(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5);

        final Vector3d containingAngularVelocity = new Vector3d();
        final Vector3d containingLinearVelocity = new Vector3d();
        final Pose3d containingPose;

        if (containingSubLevel != null) {
            if (containingSubLevel.isRemoved()) {
                throw new RuntimeException("Sub-level assembly attempted inside plot of already removed sub-level");
            }

            containingPose = new Pose3d(containingSubLevel.logicalPose());

            containingPose.transformPosition(pose.position());
            pose.orientation().set(containingPose.orientation());

            final RigidBodyHandle containingHandle = physicsSystem.getPhysicsHandle((ServerSubLevel) containingSubLevel);

            containingHandle.getLinearVelocity(containingLinearVelocity);
            containingHandle.getAngularVelocity(containingAngularVelocity);
        } else {
            containingPose = null;
        }

        final ServerSubLevel subLevel = (ServerSubLevel) container.allocateNewSubLevel(pose);

        final LevelPlot plot = subLevel.getPlot();
        plot.newEmptyChunk(plot.getCenterChunk());

        final BlockPos plotAnchor = plot.getCenterBlock();
        final SubLevelAssemblyHelper.AssemblyTransform transform = new SubLevelAssemblyHelper.AssemblyTransform(anchor, plotAnchor, 0, Rotation.NONE, level);
        SubLevelAssemblyHelper.moveOtherStuff(level, transform, blocks, bounds);
        SubLevelAssemblyHelper.moveBlocks(level, transform, blocks, null);

        final Vector3dc centerOfMass = subLevel.getMassTracker().getCenterOfMass();
        Vec3 subLevelCenter = Vec3.atLowerCornerOf(anchor);

        if (centerOfMass != null) {
            subLevelCenter = subLevelCenter
                    .subtract(Vec3.atLowerCornerOf(plotAnchor))
                    .add(centerOfMass.x(), centerOfMass.y(), centerOfMass.z());
        } else {
            subLevel.logicalPose().rotationPoint()
                    .set(plotAnchor.getX() + 0.5, plotAnchor.getY() + 0.5, plotAnchor.getZ() + 0.5);
        }

        subLevel.logicalPose().position().set(subLevelCenter.x, subLevelCenter.y, subLevelCenter.z);

        final PhysicsPipeline pipeline = physicsSystem.getPipeline();

        if (containingSubLevel != null) {
            kickFromContainingSubLevel(pipeline, subLevel, containingLinearVelocity, containingAngularVelocity, containingPose, !containingSubLevel.isRemoved() ? containingSubLevel : null);
        }

        if (!subLevel.isRemoved()) {
            pipeline.teleport(subLevel, subLevel.logicalPose().position(), subLevel.logicalPose().orientation());
        }

        subLevel.updateLastPose();

        SubLevelAssemblyHelper.moveTrackingPoints(level, bounds, subLevel, transform);

        return subLevel;
    }

    @ApiStatus.Internal
    public static void kickFromContainingSubLevel(final ServerLevel level,
                                                  final SubLevelPhysicsSystem physicsSystem,
                                                  final PhysicsPipeline pipeline,
                                                  final ServerSubLevel subLevel,
                                                  final SubLevel containingSubLevel) {

        final RigidBodyHandle containingHandle = physicsSystem.getPhysicsHandle((ServerSubLevel) containingSubLevel);
        final Vector3d linearVelocity = containingHandle.getLinearVelocity(new Vector3d());
        final Vector3d angularVelocity = containingHandle.getAngularVelocity(new Vector3d());
        final Pose3d containingPose = containingSubLevel.logicalPose();

        kickFromContainingSubLevel(pipeline, subLevel, linearVelocity, angularVelocity, containingPose, containingSubLevel);
    }

    @ApiStatus.Internal
    private static void kickFromContainingSubLevel(final PhysicsPipeline pipeline,
                                                   final ServerSubLevel subLevel,
                                                   final Vector3d containingLinearVelocity,
                                                   final Vector3d containingAngularVelocity,
                                                   final Pose3d containingPose,
                                                    final SubLevel containingSubLevel) {
        final Pose3d originalPose = new Pose3d(subLevel.logicalPose());


        // re-transform after center of mass is fixed
        // we don't need to set the orientation again as it couldn't have changed
        containingPose.transformPosition(subLevel.logicalPose().position());

        final Vector3d localPos = subLevel.logicalPose().position().sub(containingPose.position(), new Vector3d());
        if (!subLevel.isRemoved()) {
            pipeline.addLinearAndAngularVelocity(subLevel, containingAngularVelocity.cross(localPos, localPos).add(containingLinearVelocity), containingAngularVelocity);
        }

        if (containingSubLevel != null) {
            subLevel.setSplitFrom((ServerSubLevel) containingSubLevel, originalPose);
        }
    }

    /**
     * Attempts to gather all connected blocks from a given assembly origin. <br/>
     * searches in a 3x3x3 area around every block
     *
     * @param gatherOrigin            Origin of the gathering process.
     * @param level                   The level this gathering is taking place in.
     * @param maximumBlocksToAssemble the maximum blocks to gather.
     * @param frontierPredicate       A specific predicate analysed per blockpos visited that is not an AIR block. Exposes the current BlockPos candidate and its blockstate.
     * @return a {@link GatherResult gather result} that holds the blocks gathered, bounds of the volume, and an error state if gathering was unsuccessful.
     */
    public static  SubLevelAssemblyHelper.GatherResult gatherConnectedBlocks(final BlockPos gatherOrigin, final ServerLevel level, final int maximumBlocksToAssemble,  final FrontierPredicate frontierPredicate) {
        final LinkedHashSet<Pair<BlockPos, BlockState>> frontier = new LinkedHashSet<>(1 << 12);
        final Set<BlockPos> blocks = new ObjectOpenHashSet<>(1 << 10);
        final LevelAccelerator accelerator = new LevelAccelerator(level);

        final BlockState gatherOriginState = accelerator.getBlockState(gatherOrigin);

        if (gatherOriginState.isAir()) {
            return new GatherResult(null, 0, null, GatherResult.State.NO_BLOCKS);
        }

        frontier.add(Pair.of(gatherOrigin, gatherOriginState));

        int minX = gatherOrigin.getX(), minY = gatherOrigin.getY(), minZ = gatherOrigin.getZ();
        int maxX = gatherOrigin.getX(), maxY = gatherOrigin.getY(), maxZ = gatherOrigin.getZ();


        int blockCount = 0;
        final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        while (!frontier.isEmpty()) {
            final Iterator<Pair<BlockPos, BlockState>> frontierIter = frontier.iterator();
            final Pair<BlockPos, BlockState> pair = frontierIter.next();
            frontierIter.remove();
            final BlockPos pos = pair.key();

            blockCount++;
            if (blockCount > maximumBlocksToAssemble) {
                return new GatherResult(null, blockCount, null, GatherResult.State.TOO_MANY_BLOCKS);
            }

            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());

            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());

            blocks.add(pos);

            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) {
                            continue;
                        }

                        // don't connect corners, only edges
                        final int absTotal = Math.abs(x) + Math.abs(y) + Math.abs(z);
                        if (absTotal == 3) {
                            continue;
                        }

                        final BlockPos candidate = mutablePos.set(pos.getX() + x, pos.getY() + y, pos.getZ() + z);

                        if (frontier.contains(candidate)) {
                            continue;
                        }

                        final Direction direction = absTotal == 1 ? Direction.fromDelta(x, y, z) : null;
                        final BlockState candidateState = accelerator.getBlockState(candidate);

                        if (candidateState.isAir()) {
                            continue;
                        }

                        if (frontierPredicate != null && !frontierPredicate.isValidConnection(pos, pair.second(), candidate, candidateState, direction)) {
                            continue;
                        }

                        if (!blocks.contains(candidate)) {
                            frontier.add(Pair.of(candidate.immutable(), candidateState));
                        }
                    }
                }
            }
        }

        final BoundingBox3i bounds = new BoundingBox3i(
                minX, minY, minZ,
                maxX, maxY, maxZ
        );

        if (blocks.isEmpty()) {
            return new GatherResult(null, blockCount, null, GatherResult.State.NO_BLOCKS);
        }

        return new GatherResult(blocks, blockCount, bounds, GatherResult.State.SUCCESS);
    }

    public static void moveTrackingPoints(final ServerLevel level, final BoundingBox3ic bounds, final ServerSubLevel subLevel, final AssemblyTransform transform) {
        final SubLevelTrackingPointSavedData data = SubLevelTrackingPointSavedData.getOrLoad(level);
        final Iterable<Pair<UUID, TrackingPoint>> points = data.getAllTrackingPoints(bounds);

        for (final Pair<UUID, TrackingPoint> entry : points) {
            final UUID key = entry.key();
            final TrackingPoint point = new TrackingPoint(
                    subLevel != null,
                    subLevel != null ? subLevel.getUniqueId() : null,
                    subLevel != null ? subLevel.getLastSerializationPointer() : null,
                    JOMLConversion.toJOML(transform.apply(JOMLConversion.toMojang(entry.value().point()))),
                    entry.value().globalPlaceholderPosition()
            );

            data.setTrackingPoint(key, point);
        }
    }

    public static void moveOtherStuff(final ServerLevel level, final AssemblyTransform transform, final Iterable<BlockPos> blocks, final BoundingBox3ic bounds) {
        final List<Entity> entities = level.getEntitiesOfClass(Entity.class, bounds.toAABB().inflate(2.0));
        final boolean needsBitSet = needsBitSet(level, bounds, entities);

        if (!needsBitSet) return;

        final BoundedBitVolume3i volume = BoundedBitVolume3i.fromBlocks(blocks);
        assert volume != null;

        for (final Entity entity : entities) {
            boolean moveEntity = false;

            if (entity instanceof final HangingEntity hangingEntity) {
                // [1.20.1 移植修复] 上游用 1.20.5+ 的 calculateSupportBox()（支撑方块盒），
                // 1.20.1 无此 API，此前退化成 getBoundingBox()（实体自身悬空薄盒，永不与被搬方块重叠），
                // 导致物理化时悬挂实体（结构图解等）被留在原地。此处按原版公式复刻：
                // 包围盒向朝向反方向平移 0.5 格落入支撑方块层，再微缩避免蹭到相邻方块。
                final Direction facing = hangingEntity.getDirection();
                final net.minecraft.world.phys.AABB supportBox = hangingEntity.getBoundingBox()
                        .move(facing.getStepX() * -0.5, facing.getStepY() * -0.5, facing.getStepZ() * -0.5)
                        .deflate(1.0E-7);
                moveEntity = BlockPos.betweenClosedStream(supportBox).anyMatch(blockPos ->
                        volume.getOccupied(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
            }

            if (moveEntity) {
                entity.setPos(transform.apply(entity.position()));
            }
        }
    }

    private static boolean needsBitSet(final ServerLevel level, final BoundingBox3ic bounds, final List<Entity> entities) {
        return !entities.isEmpty();
    }

    /**
     * For what good is the movement of a king if his people do not follow?
     */
    public static void moveBlocks(final ServerLevel level, final AssemblyTransform transform, final Iterable<BlockPos> blocks, final SubLevel sourceSubLevel) {
        final ServerLevel resultingLevel = transform.resultingLevel;

        final LevelAccelerator accelerator = new LevelAccelerator(level);
        final LevelAccelerator resultingAccelerator = new LevelAccelerator(resultingLevel);
        final BlockState airState = Blocks.AIR.defaultBlockState();

        final List<BlockState> states = new ArrayList<>();

        BlockPos firstBlock = null;
        Vector2i chunkBoundsMin = null;
        Vector2i chunkBoundsMax = null;
        for (final BlockPos block : blocks) {
            if (firstBlock == null) {
                firstBlock = block;
            }
            final ChunkPos chunk = new ChunkPos(transform.apply(block));

            final Vector2i jomlChunkPos = new Vector2i(chunk.x, chunk.z);
            if (chunkBoundsMin == null) {
                chunkBoundsMin = new Vector2i(jomlChunkPos);
                chunkBoundsMax = new Vector2i(jomlChunkPos);
            }

            chunkBoundsMin.min(jomlChunkPos);
            chunkBoundsMax.max(jomlChunkPos);
        }

        final SubLevel subLevel = Sable.HELPER.getContaining(level, transform.apply(firstBlock));
        if (subLevel != null) {
            final LevelPlot plot = subLevel.getPlot();

            for (int chunkX = chunkBoundsMin.x; chunkX <= chunkBoundsMax.x; chunkX++) {
                for (int chunkZ = chunkBoundsMin.y; chunkZ <= chunkBoundsMax.y; chunkZ++) {
                    if (plot.getChunkHolder(plot.toLocal(new ChunkPos(chunkX, chunkZ))) == null) {
                        plot.newEmptyChunk(new ChunkPos(chunkX, chunkZ));
                    }
                }
            }
        }

        SableAssemblyPlatform.INSTANCE.setIgnoreOnPlace(resultingLevel, true);
        for (final BlockPos block : blocks) {
            final LevelChunk sourceChunk;
            final BlockState state;
            if (sourceSubLevel != null) {
                // [1.20.1 port fix] 源方块真实存在子关卡 plot chunk（偏移坐标），
                // 主世界 Level 在 2048 万格处已卸载，accelerator 读到的是 air。
                // [BUG-35] 该 plot 区块可能尚未创建 / 已卸载，getChunk 返回 null：
                // 此时该位置本就没有方块，按空气处理即可，避免 NPE 让解体直接崩游戏。
                sourceChunk = sourceSubLevel.getPlot().getChunk(sourceSubLevel.getPlot().toLocal(new ChunkPos(block)));
                if (sourceChunk == null) {
                    // [BUG-35] 源 plot 区块不存在：该位置无方块，按空气处理。
                    // 限流打一行，便于确认是否确有方块被跳过（而非整段漏搬）。
                    final long sable$now = System.currentTimeMillis();
                    if (sable$now - sable$lastNullSourceChunkLog > 1000L) {
                        sable$lastNullSourceChunkLog = sable$now;
                        Sable.LOGGER.warn("[BUG35·空源区块] 解体时源 plot 区块不存在，跳过位置 {}（子关卡 {}），疑似该 plot 区块未创建/已卸载",
                                block, sourceSubLevel.getUniqueId());
                    }
                    state = airState;
                } else {
                    state = sourceChunk.getBlockState(block);
                }
            } else {
                sourceChunk = null;
                state = accelerator.getBlockState(block);
            }
            final BlockPos newPos = transform.apply(block);

            try {
                final BlockState subLevelState = transform.apply(state);

                if (state.getBlock() instanceof final BlockSubLevelAssemblyListener listener) {
                    listener.beforeMove(level, resultingLevel, state, block, newPos);
                }

                // [BUG-35] 源区块为空时（sourceChunk == null）不应回退到主世界读取方块实体，
                // 该位置本就无方块，方块实体也应为 null。
                final BlockEntity blockEntity;
                if (sourceSubLevel != null && sourceChunk != null) {
                    blockEntity = sourceChunk.getBlockEntity(block);
                } else if (sourceSubLevel == null) {
                    blockEntity = level.getBlockEntity(block);
                } else {
                    blockEntity = null;
                }

                CompoundTag tag = null;

                if (blockEntity != null) {
                    tag = blockEntity.saveWithFullMetadata();

                    tag.putInt("x", newPos.getX());
                    tag.putInt("y", newPos.getY());
                    tag.putInt("z", newPos.getZ());
                }

                if (state.is(SableTags.SILENT_ASSEMBLY_REMOVAL)) {
                    if (sourceSubLevel != null && sourceChunk != null) {
                        sourceChunk.removeBlockEntity(block);
                    } else {
                        level.removeBlockEntity(block);
                    }
                } else {
                    // This is the "correct" way to remove a block from the world, but many mods do not implement
                    // Clearable correctly. The above tag exists to allow this issue to be "fixed" on a case-by-case
                    // basis without updating a mod's code
                    //
                    // A real solution is to implement Clearable on all block entities that can be cleared in the
                    // same way as Vanilla MC. See SetBlockCommand
                    if (blockEntity instanceof final RandomizableContainerBlockEntity container) {
                        container.setLootTable(null, 0L);
                    }
                    Clearable.tryClear(blockEntity);
                }

                final LevelChunk chunk = resultingAccelerator.getChunk(SectionPos.blockToSectionCoord(newPos.getX()), SectionPos.blockToSectionCoord(newPos.getZ()));

                chunk.setBlockState(newPos, subLevelState, true);
                states.add(subLevelState);

                final BlockEntity newBlockEntity = resultingLevel.getBlockEntity(newPos);

                if (newBlockEntity != null && tag != null) {
                    newBlockEntity.load(tag);
                }

                if (state.getBlock() instanceof final BlockSubLevelAssemblyListener listener) {
                    listener.afterMove(level, resultingLevel, state, block, newPos);
                }

                level.onBlockStateChange(newPos, airState, state);
            } catch (final Exception e) {
                Sable.LOGGER.error("Failed to move block {} at {} to {}", state, block, newPos, e);
            }
        }
        SableAssemblyPlatform.INSTANCE.setIgnoreOnPlace(resultingLevel, false);

        int i = 0;
        for (final BlockPos untransformed : blocks) {
            final BlockPos pos = transform.apply(untransformed);

            try {
                final LevelChunk levelchunk = resultingAccelerator.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
                final BlockState subLevelState = states.get(i);
                SubLevelAssemblyHelper.markAndNotifyBlock(resultingLevel, pos, levelchunk, airState, subLevelState, 3, 512);
            } catch (final Exception e) {
                Sable.LOGGER.error("Failed to mark & notify block {} (untransformed = {})", pos, untransformed, e);
            }

            i++;
        }

        SableAssemblyPlatform.INSTANCE.setIgnoreOnPlace(resultingLevel, true);
        // destroy all the old blocks
        for (final BlockPos block : blocks) {
            try {
                if (sourceSubLevel != null) {
                    // [1.20.1 port fix] 旧方块在子关卡 plot chunk，从 plot 清，不是主世界。
                    // [BUG-35] 该 plot 区块可能为空（null），跳过即可，无残留方块实体需要清除。
                    final LevelChunk srcChunk = sourceSubLevel.getPlot().getChunk(sourceSubLevel.getPlot().toLocal(new ChunkPos(block)));
                    if (srcChunk != null) {
                        level.onBlockStateChange(block, srcChunk.getBlockState(block), airState);
                        srcChunk.setBlockState(block, airState, true);
                        // [1.20.1 移植修复·残留方块实体] 强制清除源位置的方块实体。
                        srcChunk.removeBlockEntity(block);
                    }
                } else {
                    final LevelChunk chunk = accelerator.getChunk(SectionPos.blockToSectionCoord(block.getX()),
                            SectionPos.blockToSectionCoord(block.getZ()));

                    level.onBlockStateChange(block, chunk.getBlockState(block), airState);
                    chunk.setBlockState(block, airState, true);
                    // [1.20.1 移植修复·残留方块实体] 强制清除源位置的方块实体。
                    chunk.removeBlockEntity(block);
                }
            } catch (final Exception e) {
                Sable.LOGGER.error("Failed to destroy old block during assembly {}", block, e);
            }
        }
        SableAssemblyPlatform.INSTANCE.setIgnoreOnPlace(resultingLevel, false);

        for (final BlockPos block : blocks) {
            final BlockState subLevelState = airState;
            resultingLevel.sendBlockUpdated(block, Blocks.STONE.defaultBlockState(), subLevelState, 3);
        }

        SubLevelAssemblyHelper.purgeStaleBlockEntities(level, accelerator, blocks, sourceSubLevel, states.size());
    }

    /**
     * [1.20.1 移植修复·残留方块实体]
     * 装配 / 拆解结束后，源区块里可能残留「方块状态已经变成空气（或换成了别的方块），
     * 但方块实体对象仍挂在区块里」的幽灵方块实体。原版 LevelChunk#setBlockState 在
     * 「该 section 只剩空气 且 目标也是空气」时会直接 return，不会触发 onRemove，
     * 于是方块实体逃过了清除。这些幽灵实体依然会被主世界逐区块渲染循环按原坐标绘制，
     * 表现为「热气球升空后，拉杆 / 组装器数字 / 推进器等结构留在地面、看起来向下偏移」。
     * 这里统一扫描源区块并强制清除，同时把变化同步给客户端。
     */
    private static void purgeStaleBlockEntities(final ServerLevel level,
                                                final LevelAccelerator accelerator,
                                                final Iterable<BlockPos> blocks,
                                                final SubLevel sourceSubLevel,
                                                final int movedCount) {
        final Set<ChunkPos> scannedChunks = new HashSet<>();

        for (final BlockPos block : blocks) {
            final ChunkPos chunkPos = new ChunkPos(block);
            if (!scannedChunks.add(chunkPos)) {
                continue;
            }

            LevelChunk sourceChunk = null;
            try {
                sourceChunk = sourceSubLevel != null
                        ? sourceSubLevel.getPlot().getChunk(sourceSubLevel.getPlot().toLocal(chunkPos))
                        : accelerator.getChunk(chunkPos.x, chunkPos.z);
            } catch (final Exception e) {
                Sable.LOGGER.error("装配/拆解时无法读取源区块 {}", chunkPos, e);
                continue;
            }

            if (sourceChunk == null) {
                continue;
            }

            for (final Map.Entry<BlockPos, BlockEntity> entry
                    : new ArrayList<>(sourceChunk.getBlockEntities().entrySet())) {
                final BlockPos stalePos = entry.getKey();
                final BlockEntity staleEntity = entry.getValue();
                final BlockState staleState = sourceChunk.getBlockState(stalePos);

                if (staleEntity.getType().isValid(staleState)) {
                    continue;
                }

                try {
                    sourceChunk.removeBlockEntity(stalePos);
                    level.sendBlockUpdated(stalePos, Blocks.STONE.defaultBlockState(), staleState, 3);
                } catch (final Exception e) {
                    Sable.LOGGER.error("清除残留方块实体失败 @ {}", stalePos, e);
                }
            }
        }
    }

    public static void markAndNotifyBlock(final Level level, final BlockPos pPos,  final LevelChunk levelchunk, final BlockState oldState, final BlockState newState, final int pFlags, final int pRecursionLeft) {
        final Block block = newState.getBlock();
        final BlockState worldState = level.getBlockState(pPos);
        if (worldState == newState) {
            if (oldState != worldState) {
                level.setBlocksDirty(pPos, oldState, worldState);
            }

            if ((pFlags & 2) != 0 && levelchunk.getFullStatus() != null && levelchunk.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING)) {
                level.sendBlockUpdated(pPos, oldState, newState, pFlags);
            }

            if ((pFlags & 1) != 0) {
                level.blockUpdated(pPos, oldState.getBlock());
                if (newState.hasAnalogOutputSignal()) {
                    level.updateNeighbourForOutputSignal(pPos, block);
                }
            }

            if ((pFlags & 16) == 0 && pRecursionLeft > 0) {
                final int i = pFlags & -34;
                oldState.updateIndirectNeighbourShapes(level, pPos, i, pRecursionLeft - 1);
                newState.updateNeighbourShapes(level, pPos, i, pRecursionLeft - 1);
                newState.updateIndirectNeighbourShapes(level, pPos, i, pRecursionLeft - 1);
            }

            level.onBlockStateChange(pPos, oldState, worldState);
        }
    }

    @FunctionalInterface
    public interface FrontierPredicate {

        /**
         * @param originPos     the pos that is attempting to connect to `pos`
         * @param originState   the state that is attempting to connect to `pos`
         * @param pos           the block we are trying to connect to
         * @param state         the state of the block we are trying to connect to
         * @param directionFrom the direction we are checking connection from, or null if the connection is along diagonals
         * @return if the connection is valid
         */
        boolean isValidConnection(BlockPos originPos, BlockState originState, BlockPos pos, BlockState state,  Direction directionFrom);

    }

    /**
     * Transform for assembly/dissasembly
     */
    public static class AssemblyTransform {

        private final BlockPos anchorPos;
        private final BlockPos resultingAnchorPos;

        /**
         * 90-degree counter clockwise increments
         */
        private final int angle;
        private final Rotation rotation;

        private final ServerLevel resultingLevel;

        public AssemblyTransform(final BlockPos anchorPos,
                                 final BlockPos resultingAnchorPos,
                                 final int angle,
                                 final Rotation rotation,
                                 final ServerLevel resultingLevel) {
            this.anchorPos = anchorPos;
            this.resultingAnchorPos = resultingAnchorPos;
            this.angle = angle;
            this.rotation = rotation;
            this.resultingLevel = resultingLevel;
        }

        public Vec3 apply(Vec3 pos) {
            pos = pos.subtract(this.anchorPos.getCenter())
                    .yRot((float) (this.angle * Math.PI / 2.0))
                    .add(this.resultingAnchorPos.getCenter());
            return pos;
        }

        public BlockPos apply(final BlockPos pos) {
            return BlockPos.containing(this.apply(pos.getCenter()));
        }

        public BlockState apply(BlockState state) {
            final Block block = state.getBlock();

            if (block instanceof BellBlock) {
                if (state.getValue(BlockStateProperties.BELL_ATTACHMENT) == BellAttachType.DOUBLE_WALL)
                    state = state.setValue(BlockStateProperties.BELL_ATTACHMENT, BellAttachType.SINGLE_WALL);
                return state.setValue(BellBlock.FACING,
                        this.rotation.rotate(state.getValue(BellBlock.FACING)));
            }

            return state.rotate(this.rotation);
        }

        public ServerLevel getLevel() {
            return this.resultingLevel;
        }

        public Rotation getRotation() {
            return this.rotation;
        }
    }

    /**
     * The result of {@link SubLevelAssemblyHelper#gatherConnectedBlocks(BlockPos, ServerLevel, int, FrontierPredicate)}  gather connected blocks.
     *
     * @param blocks        The blocks gathered during the process.
     * @param boundingBox   The total bounding box for this gathering
     * @param checkedBlocks How many blocks were checked in the process.
     * @param assemblyState The error state of this process.
     */
    public record GatherResult( Set<BlockPos> blocks, int checkedBlocks,  BoundingBox3i boundingBox,
                               State assemblyState) {
        public enum State {
            SUCCESS("commands.sable.sub_level.assemble.connected.success"),
            TOO_MANY_BLOCKS("commands.sable.sub_level.assemble.connected.too_many_blocks"),
            NO_BLOCKS("commands.sable.sub_level.assemble.no_blocks");

            public final String errorKey;

            State(final String errorKey) {
                this.errorKey = errorKey;
            }
        }
    }
}
