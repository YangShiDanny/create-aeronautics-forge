package dev.eriksonn.aeronautics.content.blocks.hot_air.gust;

import dev.eriksonn.aeronautics.content.particle.AirPoofParticleData;
import dev.eriksonn.aeronautics.content.particle.GustParticleData;
import dev.eriksonn.aeronautics.index.AeroEntityTypes;
import dev.eriksonn.aeronautics.index.AeroSoundEvents;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.util.SableBufferUtils;
import dev.ryanhcode.sable.util.SableNBTUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3d;

public class GustEntity extends Entity implements IEntityAdditionalSpawnData {
    private final Quaterniond orientation = new Quaterniond();
    private boolean spawnedInitialBurst = false;

    public static void addGust(final Level level, final BlockPos pos, final Direction direction) {
        final Quaterniond orientation = new Quaterniond(direction.getRotation());

        final GustEntity gust = new GustEntity(AeroEntityTypes.GUST.get(), level, orientation);
        gust.setPos(pos.getCenter());

        level.addFreshEntity(gust);
    }

    public GustEntity(final EntityType<?> entityType, final Level level) {
        this(entityType, level, JOMLConversion.QUAT_IDENTITY);
    }

    public GustEntity(final EntityType<?> entityType, final Level level, final Quaterniondc orientation) {
        super(entityType, level);
        this.orientation.set(orientation);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            this.spawnClientEffects();
        } else {
            final SubLevel subLevel = Sable.HELPER.getContaining(this);

            // FIXME: this should be moved to something physics-tick dependant
            //  but we don't have a great way to do that for entities when this is
            //  being written

            if (subLevel instanceof final ServerSubLevel serverSubLevel) {
                final Vector3d forceDir = this.orientation.transform(new Vector3d(0.0, 1.0, 0.0))
                        .mul(-3.0);

                RigidBodyHandle.of(serverSubLevel)
                        .applyImpulseAtPoint(this.position(), JOMLConversion.toMojang(forceDir));
            }

            if (this.tickCount > 5)
                this.remove(RemovalReason.DISCARDED);
        }
    }

    private void spawnClientEffects() {
        final Level level = this.level();

        if (!this.spawnedInitialBurst) {
            final Vec3 soundPos = this.position();
            level.playLocalSound(soundPos.x, soundPos.y, soundPos.z, AeroSoundEvents.GUST.event(), SoundSource.BLOCKS, 0.65f, 0.35f, false);

            final int poofParticleCount = 30;

            for (int i = 0; i < 3; i++) {
                // 10% chance no gustuous action
                if (level.random.nextFloat() < 0.1) {
                    continue;
                }

                final Quaternionf particleOrientation = new Quaternionf(this.orientation);
                particleOrientation.rotateY((float) (Math.PI * 2 / 3 * i   ));
                particleOrientation.rotateZ((float) Math.toRadians(-10.0));

                final float randomRot = (float) Math.toRadians(12f);
                particleOrientation.rotateX(this.random.nextFloat() * randomRot - randomRot / 2);
                particleOrientation.rotateZ(this.random.nextFloat() * randomRot - randomRot / 2);
                particleOrientation.rotateY(this.random.nextFloat() * randomRot - randomRot / 2);

                final Vector3d particlePos = JOMLConversion.toJOML(this.position());

                particlePos.add(particleOrientation.transform(new Vector3d(0.5, 0.5, 0.0)));

                level.addParticle(new GustParticleData(particleOrientation), particlePos.x, particlePos.y, particlePos.z, 0.0f, 0.0f, 0.0f);
            }

            for (int i = 0; i < poofParticleCount; i++) {
                final Vector3d outDir = this.orientation.transform(new Vector3d(0.0, 1.0, 0.0));
                final float velocity = 0.07f + this.random.nextFloat() * 0.1f;

                final double vx = outDir.x * velocity + this.random.nextGaussian() * 0.01;
                final double vy = outDir.y * velocity + this.random.nextGaussian() * 0.01;
                final double vz = outDir.z * velocity + this.random.nextGaussian() * 0.01;

                final float positionalRandomness = 0.35f * 2f;
                final Vec3 particlePos = this.position().subtract(outDir.x, outDir.y, outDir.z).add(
                        positionalRandomness * (this.random.nextFloat() - 0.5),
                        positionalRandomness * (this.random.nextFloat() - 0.5),
                        positionalRandomness * (this.random.nextFloat() - 0.5)
                );

                level.addParticle(new AirPoofParticleData(), particlePos.x, particlePos.y, particlePos.z, vx, vy, vz);
            }

            this.spawnedInitialBurst = true;
        }
    }

    @Override
    public  PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    protected  AABB makeBoundingBox() {
        final AABB boundingBox = this.getDimensions(this.getPose()).makeBoundingBox(this.position());
        return boundingBox.move(0, -boundingBox.getYsize() / 2.0, 0);
    }

    @Override
    protected void defineSynchedData() {

    }

    @Override
    protected void readAdditionalSaveData(final CompoundTag compoundTag) {
        compoundTag.put("GustOrientation", SableNBTUtils.writeQuaternion(this.orientation));
    }

    @Override
    protected void addAdditionalSaveData(final  CompoundTag compoundTag) {
        this.orientation.set(SableNBTUtils.readQuaternion(compoundTag));
    }

    // [Port][Forge 1.20.1] NeoForge 1.21 的 IEntityWithComplexSpawn 生成数据是网络层自动附加的；
    // Forge 1.20.1 必须覆写 getAddEntityPacket 走 NetworkHooks，否则 writeSpawnData 永远不发送，
    // 客户端收不到 NBT（蜂蜜胶边界只剩一个点 / 图解朝向尺寸缺失隐形）。
    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void writeSpawnData(final  FriendlyByteBuf buffer) {
        SableBufferUtils.write(buffer, this.orientation);
    }

    @Override
    public void readSpawnData(final  FriendlyByteBuf buffer) {
        SableBufferUtils.read(buffer, this.orientation);
    }
}
