package dev.ryanhcode.sable.forge.compatibility.flywheel;

import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

import java.util.Objects;
import java.util.UUID;

/**
 * Flywheel compatibility layer for the Forge 1.20.1 port.
 *
 * <p>原本这一层在 Forge 上是 no-op stub（真实实现依赖 NeoForge 的 Veil 渲染 API）。
 * 但实测 NeoForge 的真实实现并不直接依赖 Veil 的渲染 API——Veil 只用来判断
 * flywheel 是否已加载。核心逻辑是维护一个「plot 坐标 → 子关卡渲染状态」的映射，
 * 每帧用 sable 自有的 {@link ClientSubLevel#renderPose(float)} 等 API 刷新该状态，
 * 供 {@code ContraptionVisualMixin} 在子关卡里把 Create 的 contraption（可动部件）
 * 变换矩阵修正到正确位置。</p>
 *
 * <p>修复前：{@link #getInfo(long)} 永远返回 null → 可动部件走原版逻辑、不套用
 * 子关卡 renderPose → 朝向/位置错乱（只显示一个面、翻 180 度才正常）。</p>
 */
public class FlywheelCompatForge {
    public static boolean FLYWHEEL_LOADED = ModList.get().isLoaded("flywheel");

    private static final Long2ObjectMap<SubLevelFlwRenderState> RENDER_POSES = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>());

    /**
     * 单方块子层级在 Forge 1.20.1 上不走 Flywheel 可视化（无 Veil 的
     * {@code VisualizationHelper.tryAddBlockEntity} 等价物），留空。
     */
    public static void tryAddVisual(final Object blockEntity) {
    }

    public static void preVisualizationFrame(final Level level, final float partialTicks) {
        final ClientSubLevelContainer container = (ClientSubLevelContainer) SubLevelContainer.getContainer(level);

        if (container == null) {
            RENDER_POSES.clear();
            return;
        }

        final ObjectIterator<Long2ObjectMap.Entry<SubLevelFlwRenderState>> iter = RENDER_POSES.long2ObjectEntrySet().iterator();

        while (iter.hasNext()) {
            final Long2ObjectMap.Entry<SubLevelFlwRenderState> entry = iter.next();
            final long pos = entry.getLongKey();
            final SubLevelFlwRenderState poseEntry = entry.getValue();

            final int plotX = ChunkPos.getX(pos);
            final int plotZ = ChunkPos.getZ(pos);

            final SubLevel subLevel = container.getSubLevel(plotX, plotZ);

            if (subLevel == null || !Objects.equals(subLevel.getUniqueId(), poseEntry.subLevelID)) {
                iter.remove();
                continue;
            }

            updateEntry(container, (ClientSubLevel) subLevel, poseEntry, partialTicks);
        }
    }

    public static SubLevelFlwRenderState getInfo(final long plotCoord) {
        return RENDER_POSES.get(plotCoord);
    }

    private static void updateEntry(final ClientSubLevelContainer container, final ClientSubLevel clientSubLevel, final SubLevelFlwRenderState poseEntry, final float partialTicks) {
        poseEntry.sceneID = container.getLightingSceneId(clientSubLevel);
        poseEntry.subLevelID = clientSubLevel.getUniqueId();
        poseEntry.renderPose.set(clientSubLevel.renderPose(partialTicks));
        poseEntry.latestSkyLightScale = clientSubLevel.getLatestSkyLightScale();
        poseEntry.centerChunk = clientSubLevel.getPlot().getCenterChunk();
    }

    public static void createRenderInfo(final Level level, final Object subLevel) {
        final ClientSubLevelContainer container = (ClientSubLevelContainer) SubLevelContainer.getContainer(level);
        if (container == null) return;

        final ClientSubLevel clientSubLevel = (ClientSubLevel) subLevel;
        final ChunkPos plotPos = clientSubLevel.getPlot().plotPos;
        final long plotCoord = ChunkPos.asLong(plotPos.x - container.getOrigin().x, plotPos.z - container.getOrigin().y);

        RENDER_POSES.computeIfAbsent(plotCoord, x -> {
            final SubLevelFlwRenderState renderState = new SubLevelFlwRenderState();
            updateEntry(container, clientSubLevel, renderState, 1.0f);
            return renderState;
        });
    }

    public static class SubLevelFlwRenderState {
        public int sceneID;
        public final Pose3d renderPose = new Pose3d();
        public UUID subLevelID;
        public float latestSkyLightScale;
        public ChunkPos centerChunk;
    }
}
