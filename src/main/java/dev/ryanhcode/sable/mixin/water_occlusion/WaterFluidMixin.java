package dev.ryanhcode.sable.mixin.water_occlusion;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionContainer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Don't spawn underwater particles in occluded areas.
 *
 * NeoForge 1.21.1 wrapped WaterFluid.animateTick, which called Level.addParticle directly.
 * In 1.20.1 there is no Fluid.animateTick; the ambient (underwater suspended) particles
 * are spawned from ClientLevel.lambda$doAnimateTick$8 (SRG m_263888_), which invokes
 * ClientLevel.addParticle (SRG m_7106_). We wrap that same addParticle call here, so the
 * occlusion check (and thus the behaviour) is preserved exactly.
 */
@Mixin(ClientLevel.class)
public class WaterFluidMixin {

    @WrapOperation(method = "m_263888_", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;m_7106_(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V", remap = false))
    public void sable$addUnderwaterParticle(final ClientLevel level, final ParticleOptions particleOptions, final double x, final double y, final double z, final double g, final double h, final double i, final Operation<Void> original) {
        final WaterOcclusionContainer<?> container = WaterOcclusionContainer.getContainer(level);

        if (container == null)
            return;

        final Vec3 pos = new Vec3(x, y, z);
        if (container.isOccluded(pos)) {
            return;
        }

        // m_7106_ (addParticle) is an instance method in 1.20.1, so the receiver
        // (this = ClientLevel) must be passed back as the first argument to the Operation.
        original.call(level, particleOptions, x, y, z, g, h, i);
    }

}
