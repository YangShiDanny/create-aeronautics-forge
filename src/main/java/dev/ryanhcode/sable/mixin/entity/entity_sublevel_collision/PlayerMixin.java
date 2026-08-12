package dev.ryanhcode.sable.mixin.entity.entity_sublevel_collision;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinhelpers.CanFallAtleastHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity {

    @Shadow public float bob;

    @Shadow @Final private Abilities abilities;

    @Shadow protected abstract boolean isStayingOnGroundSurface();

    @Unique
    protected boolean isAboveGround(final float f) {
        return this.onGround() || this.fallDistance < f && !this.level().noCollision(this.getBoundingBox().move(0.0, -0.2F, 0.0));
    }

    @Unique
    protected boolean canFallAtLeast(final double d, final double e, final float f) {
        final AABB bounds = this.getBoundingBox();
        final AABB below = new AABB(bounds.minX, bounds.minY - (double) f, bounds.minZ, bounds.maxX, bounds.minY, bounds.maxZ);
        final AABB moved = below.move(d, 0.0, e);
        if (d >= -1.0E-5F && d <= 1.0E-5F && e >= -1.0E-5F && e <= 1.0E-5F) {
            return true;
        }
        final boolean original = this.level().noCollision(moved);
        if (!original) return false;
        return CanFallAtleastHelper.canFallAtleastWithSubLevels(this.level(), moved) == null;
    }

    protected PlayerMixin(final EntityType<? extends LivingEntity> entityType, final Level level) {
        super(entityType, level);
    }

    @Inject(method = "maybeBackOffFromEdge", at = @At("HEAD"), cancellable = true)
    private void sable$maybeBackOffFromEdge(final Vec3 movement, final MoverType moverType, final CallbackInfoReturnable<Vec3> cir) {
        final SubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel(this);

        if (trackingSubLevel != null) {
            final float maxUpStep = this.maxUpStep();
            if (!this.abilities.flying
                    && !(movement.y > 0.0)
                    && (moverType == MoverType.SELF || moverType == MoverType.PLAYER)
                    && this.isStayingOnGroundSurface()
                    && this.isAboveGround(maxUpStep)
            ) {
                final Pose3dc pose = trackingSubLevel.lastPose();

                final double originalYaw = pose.orientation().getEulerAnglesYXZ(new Vector3d()).y;
                final Quaterniondc frameOrientation = new Quaterniond().rotateY(originalYaw);
//                final Quaterniondc frameOrientation = new Quaterniond(pose.orientation());

                final Vector3dc localMovement = frameOrientation.transformInverse(new Vector3d(movement.x, 0.0, movement.z));

                double xMovement = localMovement.x();
                double zMovement = localMovement.z();
                final double step = 0.05;

                final double signedStep = Math.signum(xMovement) * step;


                final double i;

                // reduce
                for (i = Math.signum(zMovement) * step; xMovement != 0.0 && this.sable$wouldSlideOff(xMovement, 0.0, maxUpStep, frameOrientation); xMovement -= signedStep) {
                    if (Math.abs(xMovement) <= step) {
                        xMovement = 0.0;
                        break;
                    }
                }

                while (zMovement != 0.0 && this.sable$wouldSlideOff(0.0, zMovement, maxUpStep, frameOrientation)) {
                    if (Math.abs(zMovement) <= step) {
                        zMovement = 0.0;
                        break;
                    }

                    zMovement -= i;
                }

                while (xMovement != 0.0 && zMovement != 0.0 && this.sable$wouldSlideOff(xMovement, zMovement, maxUpStep, frameOrientation)) {
                    if (Math.abs(xMovement) <= step) {
                        xMovement = 0.0;
                    } else {
                        xMovement -= signedStep;
                    }

                    if (Math.abs(zMovement) <= step) {
                        zMovement = 0.0;
                    } else {
                        zMovement -= i;
                    }
                }

                final Vector3d globalMovement = frameOrientation.transform(new Vector3d(xMovement, 0.0, zMovement));
                final Vec3 finalMovement = new Vec3(globalMovement.x, movement.y, globalMovement.z);

                cir.setReturnValue(finalMovement);
            }
        }
    }

    @Unique
    private boolean sable$wouldSlideOff(final double localXMovement, final double localZMovement, final float fallDistance, final Quaterniondc frameOrientation) {
        final Vector3d movement = new Vector3d(localXMovement, 0.0, localZMovement);
        frameOrientation.transform(movement);

        final double xMovement = movement.x;
        final double zMovement = movement.z;

        final AABB bounds = this.getBoundingBox();
        final AABB boundsToCheck = new AABB(bounds.minX + xMovement, bounds.minY - (double) fallDistance - 1.0E-5F, bounds.minZ + zMovement, bounds.maxX + xMovement, bounds.minY, bounds.maxZ + zMovement);

        return CanFallAtleastHelper.canFallAtleastWithSubLevels(this.level(), boundsToCheck) == null;
    }

    // sable$noCollision sub-level check is now inlined into the @Unique canFallAtLeast above (1.20.1 has no Player.canFallAtLeast)
}
