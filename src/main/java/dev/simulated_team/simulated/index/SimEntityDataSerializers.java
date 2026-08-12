package dev.simulated_team.simulated.index;

import dev.ryanhcode.sable.util.SableBufferUtils;
import dev.simulated_team.simulated.service.SimEntityDataSerialization;
import net.minecraft.network.FriendlyByteBuf;
import dev.simulated_team.simulated.libs.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public class SimEntityDataSerializers {

    public static final EntityDataSerializer<Vec3> VEC3 = new EntityDataSerializer<>() {
        @Override
        public Vec3 read(final FriendlyByteBuf buf) {
            final Vector3d d = SableBufferUtils.read(buf, new Vector3d());
            return new Vec3(d.x, d.y, d.z);
        }

        @Override
        public void write(final FriendlyByteBuf buf, final Vec3 v) {
            SableBufferUtils.write(buf, new Vector3d(v.x, v.y, v.z));
        }

        @Override
        public Vec3 copy(final Vec3 v) {
            return new Vec3(v.x, v.y, v.z);
        }
    };

    public static void register() {
        SimEntityDataSerialization.INSTANCE.registerDataSerializer("vec3", VEC3);
    }
}
