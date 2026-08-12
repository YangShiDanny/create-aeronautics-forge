package dev.ryanhcode.sable.mixin.player_standup;

// NOTE (1.20.1 port): This mixin is DISABLED for Forge 1.20.1.
// It wrapped `Player.canPlayerFitWithinBlocksAndEntitiesWhen(AABB)` (added in 1.21),
// intercepting the inner `Level.noCollision(Entity, AABB)` to also check sublevel blocks.
// In 1.20.1 the Player class has NO equivalent hookable method (no `canPlayerFitWithinBlocksAndEntitiesWhen`,
// no `isFree(AABB)`); the only `noCollision(Entity, AABB)` callers are `maybeBackOffFromEdge` and the
// private `isAboveGround()`, neither of which is the "player stand-up fit" check. Since the injection
// target API does not exist in 1.20.1, the mixin is removed from sable.mixins.json so it no longer
// crashes at load. The sublevel collision feature is still provided by entity_sublevel_collision
// (maybeBackOffFromEdge / isAboveGround / canFallAtLeast) and interaction_distance.EntityMixin (distanceTo).
// TODO (optional, phase 2): re-implement player stand-up sublevel collision by wrapping
// `maybeBackOffFromEdge` in entity_sublevel_collision, or by a @Unique helper invoked from there.

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.mixinhelpers.CanFallAtleastHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public class PlayerMixin {

    @WrapOperation(
            method = "canPlayerFitWithinBlocksAndEntitiesWhen",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z", remap = false)
    )
    private boolean sable$noCollisionWithSubLevels(final Level instance, final Entity entity, final AABB aabb, final Operation<Boolean> original) {
        if (!original.call(instance, entity, aabb)) {
            return false;
        }

        // If vanilla says no collision, also check sublevel blocks.
        // canFallAtleastWithSubLevels returns non-null when there IS a collision,
        // meaning the player does NOT fit → return false.
        return CanFallAtleastHelper.canFallAtleastWithSubLevels(instance, aabb) == null;
    }
}
