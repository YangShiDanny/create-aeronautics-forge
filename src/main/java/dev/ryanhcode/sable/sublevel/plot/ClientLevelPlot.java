package dev.ryanhcode.sable.sublevel.plot;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.ticks.LevelChunkTicks;
import java.util.function.BooleanSupplier;

/**
 * An allocated & reserved space in a level belonging to a {@link SubLevel}, holding its own chunk grid.
 */
public class ClientLevelPlot extends LevelPlot {
    /**
     * The light engine for this plot. Mirror of {@link ServerLevelPlot}:
     * a dedicated engine so light can flood-fill across plot chunks instead of being
     * truncated by the main-world light engine at the remote offset coordinates.
     */
    protected final LevelLightEngine lightEngine;

    /**
     * Creates a new plot at the given plot coordinate.
     *
     * @param plotContainer the parent plot container of this level plot
     * @param x             the global X coordinate of the plot, in units of {@code 1 << logSize} chunks
     * @param z             the global Z coordinate of the plot, in units of {@code 1 << logSize} chunks
     * @param logSize       the log_2 of the side length of a plot
     * @param subLevel      the sub-level using this plot
     */
    public ClientLevelPlot(final SubLevelContainer plotContainer, final int x, final int z, final int logSize, final ClientSubLevel subLevel) {
        super(plotContainer, x, z, logSize, subLevel);

        final ClientLevel level = subLevel.getLevel();
        final LevelLightEngine parentLightEngine = level.getLightEngine();
        // 使用一个能按 SubLevel 全局坐标查找 plot chunk 的 chunk source，
        // 让 LevelLightEngine 在做方块光/天空光 flood-fill 时能找到真正的邻居 chunk。
        final PlotLightChunkSource lightChunkSource = new PlotLightChunkSource(level, (ClientSubLevelContainer) plotContainer, this);
        this.lightEngine = new LevelLightEngine(lightChunkSource,
                parentLightEngine.blockEngine != null, parentLightEngine.skyEngine != null);
    }

    /**
     * Returns the lighting engine this sub-level should use.
     * Unlike the previous implementation that reused the main {@link ClientLevel}'s engine,
     * this returns a dedicated per-plot engine so block light can propagate across plot chunks.
     *
     * @return the lighting engine for this plot
     */
    @Override
    public LevelLightEngine getLightEngine() {
        return this.lightEngine;
    }

    private void initializeLight(final LevelChunk chunk) {
        final ClientLevel level = this.getSubLevel().getLevel();
        final ChunkPos pos = chunk.getPos();
        final LevelLightEngine lightEngine = this.lightEngine;

        for (int i = 0; i < chunk.getSectionsCount(); i++) {
            final LevelChunkSection section = chunk.getSection(i);
            // updateSectionStatus(sectionPos, true) 表示该区段含非空气方块（参与光照计算）。
            // 与 ClientPacketListener.enableChunkLight 的 !hasOnlyAir() 语义一致。
            lightEngine.updateSectionStatus(SectionPos.of(pos, level.getSectionYFromSectionIndex(i)), !section.hasOnlyAir());
        }

        lightEngine.setLightEnabled(pos, chunk.isLightCorrect());
        lightEngine.retainData(pos, false);
    }

    private void correctLight(final LevelChunk chunk) {
        if (chunk.isLightCorrect()) {
            return;
        }

        this.lightEngine.propagateLightSources(chunk.getPos());
        chunk.setLightCorrect(true);
    }

    private void lightChunk(final LevelChunk chunk) {
        chunk.initializeLightSources();
        this.initializeLight(chunk);
        this.correctLight(chunk);
    }

    /**
     * @return the sub-level using this plot.
     */
    @Override
    public ClientSubLevel getSubLevel() {
        return (ClientSubLevel) super.getSubLevel();
    }

    @Override
    protected void onRemoveChunkHolder(final LevelChunk levelChunk) {
        ((ClientLevel) levelChunk.getLevel()).unload(levelChunk);
    }

    @Override
    public void addChunkHolder(final ChunkPos localChunkPos, final PlotChunkHolder holder, final boolean initializeLighting) {
        super.addChunkHolder(localChunkPos, holder, initializeLighting);

        final LevelChunk chunk = holder.getChunk();
        if (initializeLighting) {
            this.lightChunk(chunk);
        }

        do {
            this.lightEngine.runLightUpdates();
        } while (this.lightEngine.hasLightWork());

        for (final BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            final BlockEntitySubLevelActor actor = blockEntity instanceof BlockEntitySubLevelActor ? (BlockEntitySubLevelActor) blockEntity : null;

            if (actor != null) {
                this.blockEntityActors.put(blockEntity.getBlockPos(), actor);
            }
        }
    }

    /**
     * Ticks this plot, running lighting updates.
     */
    @Override
    public void tick() {
        do {
            this.lightEngine.runLightUpdates();
        } while (this.lightEngine.hasLightWork());
    }

    /**
     * A chunk source used only by the plot's {@link LevelLightEngine}.
     * It resolves global chunk coordinates back into the client plotgrid so that
     * light can propagate across the plot's own chunks instead of looking for
     * non-existent main-world chunks at the remote offset coordinates.
     */
    private static final class PlotLightChunkSource extends ChunkSource {
        private final ClientLevel level;
        private final ClientSubLevelContainer container;
        private final ClientLevelPlot plot;

        PlotLightChunkSource(final ClientLevel level, final ClientSubLevelContainer container, final ClientLevelPlot plot) {
            this.level = level;
            this.container = container;
            this.plot = plot;
        }

        @Override
        public Level getLevel() {
            return this.level;
        }

        @Override
        public ChunkAccess getChunk(final int x, final int z, final ChunkStatus status, final boolean load) {
            final ChunkPos pos = new ChunkPos(x, z);
            final PlotChunkHolder holder = this.container.getChunkHolder(pos);
            if (holder != null) {
                return holder.getChunk();
            }

            // Out of plot bounds or not loaded: return an empty chunk so the light engine
            // treats it as a boundary rather than crashing or propagating into the void.
            return new LevelChunk(this.level, pos, UpgradeData.EMPTY,
                    new LevelChunkTicks<>(), new LevelChunkTicks<>(), 0L,
                    this.createEmptySections(), null, null);
        }

        @Override
        public LightChunk getChunkForLighting(final int x, final int z) {
            return (LightChunk) this.getChunk(x, z, ChunkStatus.FULL, false);
        }

        @Override
        public boolean hasChunk(final int x, final int z) {
            return this.container.getChunkHolder(new ChunkPos(x, z)) != null;
        }

        @Override
        public void tick(final BooleanSupplier p_200895_, final boolean p_200896_) {
            // 仅用于光照的 chunk source，无需处理区块的加载/卸载。
        }

        @Override
        public String gatherStats() {
            return "PlotLightChunkSource";
        }

        @Override
        public int getLoadedChunksCount() {
            int count = 0;
            for (final var subLevel : this.container.getAllSubLevels()) {
                count += subLevel.getPlot().getLoadedChunks().size();
            }
            return count;
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return this.plot.lightEngine;
        }

        @Override
        public void updateChunkForced(final ChunkPos pos, final boolean forced) {
            // No-op: plot chunks are managed by the plot system, not by forced tickets.
        }

        private LevelChunkSection[] createEmptySections() {
            final int sectionCount = this.level.getSectionsCount();
            final LevelChunkSection[] sections = new LevelChunkSection[sectionCount];
            for (int i = 0; i < sectionCount; i++) {
                sections[i] = new LevelChunkSection(this.level.registryAccess().registryOrThrow(Registries.BIOME));
            }
            return sections;
        }
    }
}
