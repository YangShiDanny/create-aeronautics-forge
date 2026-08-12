package dev.ryanhcode.sable.physics.impl.rapier;

import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineProvider;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

@PhysicsPipelineProvider.LoadPriority()
public final class RapierPhysicsPipelineProvider implements PhysicsPipelineProvider {

    @Override
    public  PhysicsPipeline createPipeline( final ServerLevel level) {
        return new RapierPhysicsPipeline(level);
    }

}
