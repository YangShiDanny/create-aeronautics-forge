package dev.ryanhcode.sable.forge.mixin.compatibility.create.inventory_manipulation;

import com.google.common.base.Predicate;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.CapManipulationBehaviourBase;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

@Mixin(CapManipulationBehaviourBase.class)
public class CapManipulationBehaviourBaseMixin {

    @Shadow protected Predicate<BlockEntity> filter;

    @Shadow protected boolean bypassSided;

    // On Forge 1.20.1, Create 6.x resolves item handlers via
    //   BlockEntity be = level.getBlockEntity(pos);
    //   be.getCapability(ForgeCapabilities.ITEM_HANDLER, face);
    // NeoForge used a Level.getCapability(BlockCapability, ...) facade that does not exist on Forge,
    // so we only redirect the getBlockEntity lookup to the sublevel-transformed position. The
    // subsequent getCapability call then naturally runs against the correct (sublevel) block entity.
    @Redirect(remap = false, method = "findNewCapability", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_7702_(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    public BlockEntity sable$findNewCapOnSubLevel(final Level level, final BlockPos blockPos) {
        final ActiveSableCompanion helper = Sable.HELPER;
        return helper.runIncludingSubLevels(level, blockPos.getCenter(), true, helper.getContaining(level, blockPos), (subLevel, internalPos) -> {
            final BlockEntity caughtBE = level.getBlockEntity(internalPos);
            if (this.filter.apply(caughtBE)) {
                return caughtBE;
            }

            return null;
        });
    }
}
