package dev.ryanhcode.sable.sublevel.render.dispatcher;

import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaSingleSubLevelRenderData;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;

public class ReachAroundSubLevelRenderDispatcher extends VanillaSubLevelRenderDispatcher {

    private ChunkRenderDispatcher sectionRenderDispatcher;

    public ReachAroundSubLevelRenderDispatcher() {
    }

    private ChunkRenderDispatcher getSectionRenderDispatcher(final LevelRenderer levelRenderer, final ClientLevel level) {
        if (this.sectionRenderDispatcher == null) {
            final Minecraft minecraft = Minecraft.getInstance();
            final RenderBuffers renderBuffers = minecraft.renderBuffers();

            this.sectionRenderDispatcher = new ChunkRenderDispatcher(
                    level, levelRenderer, Util.backgroundExecutor(),
                    minecraft.is64Bit(),
                    renderBuffers.fixedBufferPack()
            );
        }

        this.sectionRenderDispatcher.setLevel(level);
        return this.sectionRenderDispatcher;
    }

    @Override
    public void rebuild(final Iterable<ClientSubLevel> sublevels) {
        if (this.sectionRenderDispatcher != null) {
            this.sectionRenderDispatcher.blockUntilClear();
        }

        super.rebuild(sublevels);
    }

    @Override
    public SubLevelRenderData createRenderData(final ClientSubLevel subLevel) {
        if (isSingleBlock(subLevel)) {
            return new VanillaSingleSubLevelRenderData(subLevel);
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final LevelRenderer levelRenderer = minecraft.levelRenderer;
        final ChunkRenderDispatcher sectionRenderDispatcher = this.getSectionRenderDispatcher(levelRenderer, subLevel.getLevel());

        return new VanillaChunkedSubLevelRenderData(subLevel, sectionRenderDispatcher);
    }

    @Override
    public void preRenderChunks(final Camera camera) {
        if (this.sectionRenderDispatcher != null) {
            final Minecraft minecraft = Minecraft.getInstance();
            this.sectionRenderDispatcher.setCamera(camera.getPosition());

            minecraft.getProfiler().push("sub_level_upload");
            this.sectionRenderDispatcher.uploadAllPendingUploads();
            minecraft.getProfiler().pop();
        }
    }

    @Override
    public void free() {
        if (this.sectionRenderDispatcher != null) {
            this.sectionRenderDispatcher.dispose();
            this.sectionRenderDispatcher = null;
        }

        super.free();
    }
}
