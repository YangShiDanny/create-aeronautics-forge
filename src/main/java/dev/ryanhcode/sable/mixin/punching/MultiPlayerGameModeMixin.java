package dev.ryanhcode.sable.mixin.punching;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.network.client.ClientSubLevelPunchHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "startDestroyBlock", at = @At("HEAD"))
    private void onBlockBreakStart(final BlockPos blockPos, final Direction direction, final CallbackInfoReturnable<Boolean> cir) {
        assert this.minecraft.player != null;

        final HitResult.Type hitType = this.minecraft.hitResult != null ? this.minecraft.hitResult.getType() : null;
        final BlockPos hitPos = this.minecraft.hitResult instanceof final BlockHitResult bhr ? bhr.getBlockPos() : null;
        final net.minecraft.world.phys.Vec3 hitLoc = this.minecraft.hitResult != null ? this.minecraft.hitResult.getLocation() : null;

        if (this.minecraft.hitResult instanceof final BlockHitResult blockHitResult) {
            ClientSubLevelPunchHelper.clientTryPunch(blockHitResult, this.minecraft.level, true);
        }
    }

}
