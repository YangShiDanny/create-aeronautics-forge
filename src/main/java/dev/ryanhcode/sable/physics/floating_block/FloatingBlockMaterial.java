package dev.ryanhcode.sable.physics.floating_block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;

public record FloatingBlockMaterial(boolean preventSelfLift, boolean scaleWithPressure, boolean scaleWithGravity, double liftStrength,
                                    double transitionSpeed, double slowVerticalFriction, double fastVerticalFriction,
                                    double slowHorizontalFriction, double fastHorizontalFriction) {
    public static final Codec<FloatingBlockMaterial> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("prevent_self_lift", false).forGetter(FloatingBlockMaterial::preventSelfLift),
            Codec.BOOL.optionalFieldOf("scale_with_pressure", false).forGetter(FloatingBlockMaterial::scaleWithPressure),
            Codec.BOOL.optionalFieldOf("scale_friction_with_gravity", false).forGetter(FloatingBlockMaterial::scaleWithGravity),
            Codec.DOUBLE.fieldOf("lift_strength").forGetter(FloatingBlockMaterial::liftStrength),
            Codec.DOUBLE.optionalFieldOf("transition_speed", 0.0d).forGetter(FloatingBlockMaterial::transitionSpeed),
            Codec.DOUBLE.optionalFieldOf("slow_vertical_friction", 0.0d).forGetter(FloatingBlockMaterial::slowVerticalFriction),
            Codec.DOUBLE.optionalFieldOf("fast_vertical_friction", 0.0d).forGetter(FloatingBlockMaterial::fastVerticalFriction),
            Codec.DOUBLE.optionalFieldOf("slow_horizontal_friction", 0.0d).forGetter(FloatingBlockMaterial::slowHorizontalFriction),
            Codec.DOUBLE.optionalFieldOf("fast_horizontal_friction", 0.0d).forGetter(FloatingBlockMaterial::fastHorizontalFriction)
    ).apply(instance, FloatingBlockMaterial::new));

    public static void encode(final FriendlyByteBuf buf, final FloatingBlockMaterial msg) {
        final CompoundTag tag = (CompoundTag) FloatingBlockMaterial.CODEC.encodeStart(NbtOps.INSTANCE, msg).result().orElseThrow();
        buf.writeNbt(tag);
    }

    public static FloatingBlockMaterial decode(final FriendlyByteBuf buf) {
        final CompoundTag tag = buf.readNbt();
        return FloatingBlockMaterial.CODEC.parse(NbtOps.INSTANCE, tag).result().orElseThrow();
    }
}
