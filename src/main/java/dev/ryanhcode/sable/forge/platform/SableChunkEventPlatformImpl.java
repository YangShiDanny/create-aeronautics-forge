package dev.ryanhcode.sable.forge.platform;

import dev.ryanhcode.sable.platform.SableChunkEventPlatform;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.ChunkEvent;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class SableChunkEventPlatformImpl implements SableChunkEventPlatform {

    @Override
    public void onClientChunkPacketReplaced(final LevelChunk chunk) {
        MinecraftForge.EVENT_BUS.post(new ChunkEvent.Load(chunk, false));
    }

    @Override
    public void onOldChunkInvalid(final LevelChunk chunk) {
        // no-op
    }

    @Override
    public void onPlotChunkLoaded(final LevelChunk chunk) {
        MinecraftForge.EVENT_BUS.post(new ChunkEvent.Load(chunk, false));
    }

}
