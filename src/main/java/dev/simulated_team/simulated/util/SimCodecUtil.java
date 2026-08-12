package dev.simulated_team.simulated.util;
import dev.simulated_team.simulated.backport.BackportCodecs;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.util.SableBufferUtils;
import io.netty.buffer.ByteBuf;
import dev.simulated_team.simulated.libs.minecraft.network.codec.ByteBufCodecs;
import dev.simulated_team.simulated.libs.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;

public class SimCodecUtil {

    public static final StreamCodec<ByteBuf, Vector3d> STREAM_VECTOR3D = StreamCodec.of(
            SableBufferUtils::write,
            x -> SableBufferUtils.read(x, new Vector3d()));

    public static final StreamCodec<ByteBuf, Vector3dc> STREAM_VECTOR3DC = ByteBufCodecs.DOUBLE.apply(ByteBufCodecs.list(3))
            .map(l -> new Vector3d(l.get(0), l.get(1), l.get(2)), (v) -> List.of(v.x(), v.y(), v.z()));

    public static final StreamCodec<ByteBuf, BoundingBox3d> BOUNDING_BOX_3D_STREAM_CODEC = ByteBufCodecs.DOUBLE.apply(ByteBufCodecs.list(6))
            .map(l -> new BoundingBox3d(l.get(0), l.get(1), l.get(2), l.get(3), l.get(4), l.get(5)), bb -> List.of(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ));

    public static final StreamCodec<ByteBuf, ForceGroup> STREAM_FORCE_GROUP = BackportCodecs.RESOURCE_LOCATION.map(ForceGroups.REGISTRY::get, ForceGroups::getKey);

    public static final StreamCodec<ByteBuf, QueuedForceGroup.PointForce> STREAM_POINT_FORCE = STREAM_VECTOR3DC.apply(ByteBufCodecs.list(2))
            .map(l -> new QueuedForceGroup.PointForce(l.get(0), l.get(1)), p -> List.of(p.point(), p.force()));

    public static <T> Codec<T> withAlternative(final Codec<T> first, final Codec<T> second) {
        return new WithAlternativeButGood<>(first, second);
    }

    private record WithAlternativeButGood<T>(Codec<T> first, Codec<T> second) implements Codec<T> {
        @Override
        public <T1> DataResult<Pair<T, T1>> decode(final DynamicOps<T1> ops, final T1 input) {
            final DataResult<Pair<T, T1>> result = this.first.decode(ops, input);
            if(result.result().isPresent())
                return result;
            return this.second.decode(ops, input);
        }

        @Override
        public <T1> DataResult<T1> encode(final T input, final DynamicOps<T1> ops, final T1 prefix) {
            final DataResult<T1> result = this.first.encode(input, ops, prefix);
            if(result.result().isPresent())
                return result;
            return this.second.encode(input, ops, prefix);
        }
    }
}
