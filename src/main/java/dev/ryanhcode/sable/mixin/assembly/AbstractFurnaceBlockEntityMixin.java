package dev.ryanhcode.sable.mixin.assembly;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin extends BaseContainerBlockEntity {

    @Shadow @Final private Object2IntOpenHashMap<ResourceLocation> recipesUsed;

    protected AbstractFurnaceBlockEntityMixin(final BlockEntityType<?> blockEntityType, final BlockPos blockPos, final BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }

    // In Forge 1.20.1 BaseContainerBlockEntity does not implement Clearable (unlike
    // NeoForge 1.21.1), so super.clearContent() is unavailable. AbstractFurnaceBlockEntity
    // itself has a public clearContent() which already clears the base inventory; we simply
    // append the recipesUsed cleanup after it runs.
    @Inject(method = "clearContent", at = @At("TAIL"))
    private void sable$clearRecipesUsed(final CallbackInfo ci) {
        this.recipesUsed.clear();
    }

}
