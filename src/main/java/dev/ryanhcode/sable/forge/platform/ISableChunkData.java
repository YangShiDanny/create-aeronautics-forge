package dev.ryanhcode.sable.forge.platform;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Forge 1.20.1 replacement for NeoForge's {@code AttachmentHolder} chunk-attachment system.
 * Sable stores its per-chunk plot/sublevel data here; the platform impl round-trips it
 * to/from the chunk save tag so it persists across chunk load/save.
 */
public interface ISableChunkData {
    
    CompoundTag getTag();

    void setTag( final CompoundTag tag);
}
