package dev.ryanhcode.sable.forge.physics.callback;

import com.simibubi.create.content.equipment.bell.AbstractBellBlock;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.physics.callback.FragileBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public class AbstractBellBlockCallback extends FragileBlockCallback {
    public static final AbstractBellBlockCallback INSTANCE = new AbstractBellBlockCallback();

    public AbstractBellBlockCallback() {}

    @Override
    public boolean shouldTriggerFor(final BlockState state) {
        return state.getBlock() instanceof AbstractBellBlock<?>;
    }

    @Override
    public CollisionResult onHit(final ServerLevel level, final BlockPos pos, final BlockState state, final Vector3d hitPos) {
        final Vec3 hitDir = pos.getCenter().subtract(hitPos.x, hitPos.y, hitPos.z);
        final Direction facing = state.getValue(AbstractBellBlock.FACING);
        final BellAttachType attachment = state.getValue(AbstractBellBlock.ATTACHMENT);

        int xMul = Math.abs(facing.getStepX());
        int zMul = Math.abs(facing.getStepZ());

        if (attachment == BellAttachType.CEILING) {
            xMul = 1;
            zMul = 1;
        }

        // phase-1: Create-compat (bell ringing on sublevels) deferred to phase-2 re-port.
        // The AbstractBellBlockAccessor mixin lives in the disabled compatibility/create tree.
        return new CollisionResult(JOMLConversion.ZERO, false);
    }
}
