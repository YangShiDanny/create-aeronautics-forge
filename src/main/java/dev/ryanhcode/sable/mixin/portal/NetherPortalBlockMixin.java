package dev.ryanhcode.sable.mixin.portal;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// NeoForge 1.21.1 redirected WorldBorder.clampToBounds inside NetherPortalBlock.getPortalDestination.
// In 1.20.1 that logic lives in Entity.findDimensionEntryPoint (SRG m_7937_), which scales the
// entity position by the dimension teleport scale and clamps to the destination world border.
// We redirect the same clampToBounds call here, projecting the entity out of its sublevel first.
@Mixin(Entity.class)
public class NetherPortalBlockMixin {

    @Shadow public double getX() { return 0.0D; }
    @Shadow public double getY() { return 0.0D; }
    @Shadow public double getZ() { return 0.0D; }
    @Shadow public Level level() { return null; }

    @Redirect(method = "findDimensionEntryPoint", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/border/WorldBorder;clampToBounds(DDD)Lnet/minecraft/core/BlockPos;"))
    private BlockPos sable$getPortalDestination(final WorldBorder instance,
                                                final double x,
                                                final double y,
                                                final double z,
                                                @Local(ordinal = 0) final double multiplier) {
        final Vec3 position = new Vec3(this.getX(), this.getY(), this.getZ());

        final Vec3 globalPos = Sable.HELPER.projectOutOfSubLevel(this.level(), position);

        return instance.clampToBounds(
                globalPos.x * multiplier,
                globalPos.y,
                globalPos.z * multiplier
        );
    }

}
