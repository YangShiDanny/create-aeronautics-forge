package dev.ryanhcode.sable.mixinterface.sublevel_render.vanilla;

import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;

public interface RenderSectionExtension {

    void sable$addDirtyListener(DirtyListener listener);

    void sable$setListening(boolean listening);

    @FunctionalInterface
    interface DirtyListener {

        void markDirty(ChunkRenderDispatcher.RenderChunk section);
    }
}
