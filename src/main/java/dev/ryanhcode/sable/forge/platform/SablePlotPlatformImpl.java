package dev.ryanhcode.sable.forge.platform;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.forge.platform.SableChunkDataProvider;
import dev.ryanhcode.sable.platform.SablePlotPlatform;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.event.level.ChunkDataEvent;
import org.slf4j.Logger;

@SuppressWarnings("UnstableApiUsage")
public class SablePlotPlatformImpl implements SablePlotPlatform {

    private static final Logger LOGGER = LogUtils.getLogger();
    // NeoForge used AttachmentHolder.ATTACHMENTS_NBT_KEY; Forge 1.20.1 has no chunk-attachment
    // API, so we store Sable's per-chunk plot data inside the chunk capability registered in SableForge.
    private static final String SABLE_ATTACHMENTS_KEY = "sable:attachments";

    // NeoForge exposed a public LevelChunkAuxiliaryLightManager wrapper; Forge 1.20.1 has no such
    // class. The vanilla LevelChunk.AuxiliaryLightManager is reached via LevelChunk#getAuxLightManager()
    // and serializes under this NBT key (same string vanilla uses internally).
    private static final String LIGHT_NBT_KEY = "LevelChunkAuxiliaryLightManager";

    @Override
    public void readLightData(final CompoundTag tag, final RegistryAccess registryAccess, final LevelChunk chunk) {
        // Forge 1.20.1 has no LevelChunk#getAuxLightManager (added in later 1.20.x / NeoForge).
        // Auxiliary-light persistence for sub-levels is disabled on this version.
    }

    @Override
    public void readChunkAttachments(final CompoundTag tag, final RegistryAccess registryAccess, final LevelChunk chunk) {
        final ISableChunkData data = chunk.getCapability(SableChunkDataProvider.CAPABILITY).resolve().orElse(null);
        if (data != null && tag.contains(SABLE_ATTACHMENTS_KEY, Tag.TAG_COMPOUND)) {
            data.setTag(tag.getCompound(SABLE_ATTACHMENTS_KEY));
        }
    }

    @Override
    public void postLoad(final CompoundTag tag, final LevelChunk chunk) {
        MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Load(chunk, tag, ChunkStatus.ChunkType.LEVELCHUNK));
    }

    @Override
    public void writeLightData(final CompoundTag tag, final RegistryAccess registryAccess, final LevelChunk chunk) {
        // Forge 1.20.1 has no LevelChunk#getAuxLightManager; auxiliary-light persistence disabled.
    }

    @Override
    public void writeChunkAttachments(final CompoundTag tag, final RegistryAccess registryAccess, final LevelChunk chunk) {
        try {
            final ISableChunkData data = chunk.getCapability(SableChunkDataProvider.CAPABILITY).resolve().orElse(null);
            if (data != null) {
                final CompoundTag capTag = data.getTag();
                if (capTag != null && !capTag.isEmpty()) {
                    tag.put(SABLE_ATTACHMENTS_KEY, capTag);
                }
            }
        } catch (final Exception e) {
            LOGGER.error("Failed to write chunk attachments. An attachment has likely thrown an exception trying to write state. It will not persist. Report this to the mod author", e);
        }
    }
}
