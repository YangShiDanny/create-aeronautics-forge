package dev.ryanhcode.sable.forge.platform;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SableChunkDataProvider implements ISableChunkData, ICapabilityProvider, INBTSerializable<CompoundTag> {

    public static final Capability<ISableChunkData> CAPABILITY =
            CapabilityManager.get(new CapabilityToken<ISableChunkData>() {});

    private CompoundTag data = new CompoundTag();
    private final LazyOptional<ISableChunkData> holder = LazyOptional.of(() -> this);

    @Override
    
    public CompoundTag getTag() {
        return data;
    }

    @Override
    public void setTag( final CompoundTag tag) {
        this.data = tag != null ? tag.copy() : new CompoundTag();
    }

    @Override
    
    @SuppressWarnings("unchecked")
    public <T> LazyOptional<T> getCapability( final Capability<T> cap,  final Direction side) {
        return CAPABILITY.orEmpty(cap, holder);
    }

    @Override
    
    public CompoundTag serializeNBT() {
        return data.copy();
    }

    @Override
    public void deserializeNBT(final CompoundTag nbt) {
        this.data = nbt.copy();
    }
}
