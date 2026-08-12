package dev.ryanhcode.sable.mixin.entity.teleport_players;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ryanhcode.sable.Sable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Shadow
    public abstract Level level();

    /**
     * @author RyanH
     * @reason Projecting out of sub-levels when teleporting with a ticket
     */
    @WrapMethod(method = "m_20324_(DDD)V", remap = false)
    public void sable$teleportToWithTicket(final double x, final double y, final double z, final Operation<Void> original) {
        final Vector3d globalPos = Sable.HELPER.projectOutOfSubLevel(this.level(), new Vector3d(x, y, z));
        original.call(globalPos.x, globalPos.y, globalPos.z);
    }
}
