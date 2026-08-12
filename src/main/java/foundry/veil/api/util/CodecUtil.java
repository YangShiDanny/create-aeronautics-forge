package foundry.veil.api.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.joml.Vector3d;

public final class CodecUtil {
    private CodecUtil() {}

    /** Backport of NeoForge veil's {@code CodecUtil.VECTOR3D_CODEC}: a {@code Codec<Vector3d>}. */
    public static final Codec<Vector3d> VECTOR3D_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("x").forGetter(Vector3d::x),
            Codec.DOUBLE.fieldOf("y").forGetter(Vector3d::y),
            Codec.DOUBLE.fieldOf("z").forGetter(Vector3d::z)
    ).apply(instance, Vector3d::new));
}
