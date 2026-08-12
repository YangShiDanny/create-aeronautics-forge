package dev.simulated_team.simulated.neoforge.mixin.self_mixins;

import dev.simulated_team.simulated.content.entities.diagram.DiagramEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(DiagramEntity.class)
public abstract class DiagramEntityMixin implements IEntityAdditionalSpawnData {
    @Shadow public abstract void addAdditionalSaveData(CompoundTag tag);

    @Shadow public abstract void readAdditionalSaveData(CompoundTag tag);

    @Override
    public void writeSpawnData(final FriendlyByteBuf registryFriendlyByteBuf) {
        final CompoundTag compound = new CompoundTag();
        this.addAdditionalSaveData(compound);
        registryFriendlyByteBuf.writeNbt(compound);
    }

    @Override
    public void readSpawnData(final FriendlyByteBuf registryFriendlyByteBuf) {
        this.readAdditionalSaveData(registryFriendlyByteBuf.readNbt());
    }
}
