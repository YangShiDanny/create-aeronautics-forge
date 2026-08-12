package dev.simulated_team.simulated.libs.catnip.codecs.stream;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import dev.simulated_team.simulated.libs.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

/**
 * Backport shim of Create's {@code dev.simulated_team.simulated.libs.catnip.codecs.stream.CatnipStreamCodecs}.
 * Create 6.0.8 for Forge 1.20.1 does NOT ship the {@code catnip} package at all, so the
 * merged aeronautics build (which references {@code CatnipStreamCodecs.HAND/VEC3}) needs these
 * to compile. They are real {@link StreamCodec}s over {@link ByteBuf}.
 */
public final class CatnipStreamCodecs {

    private CatnipStreamCodecs() {
    }

    public static final StreamCodec<ByteBuf, InteractionHand> HAND =
            StreamCodec.of(
                    (b, v) -> ((FriendlyByteBuf) b).writeEnum(v),
                    b -> ((FriendlyByteBuf) b).readEnum(InteractionHand.class));

    public static final StreamCodec<ByteBuf, Vec3> VEC3 =
            StreamCodec.of(
                    (b, v) -> {
                        b.writeDouble(v.x);
                        b.writeDouble(v.y);
                        b.writeDouble(v.z);
                    },
                    b -> new Vec3(b.readDouble(), b.readDouble(), b.readDouble()));
}
