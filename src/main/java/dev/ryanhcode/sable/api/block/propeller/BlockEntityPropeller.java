package dev.ryanhcode.sable.api.block.propeller;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;

/**
 * Spinny spin spin, woosh woosh!
 */
public interface BlockEntityPropeller {

    /**
     * @return the direction of the propeller
     */
    Direction getBlockDirection();

    /**
     * @return airflow in units of [m/s]
     */
    double getAirflow();

    /**
     * @return thrust in [pN]
     */
    double getThrust();

    /**
     * @return if the propeller is active / thrust should be computed
     */
    boolean isActive();

    /**
     * @return the thrust scaled by -1 * airflow scaling * air pressure
     */
    default double getScaledThrust() {
        return -this.getThrust() * this.getAirflowScaling() * this.getCurrentAirPressure();
    }

    default double getCurrentAirPressure() {
        final Level level = this.getLevel();
        return DimensionPhysicsData.getAirPressure(level, Sable.HELPER.projectOutOfSubLevel(level, JOMLConversion.toJOML(this.getBlockPos().getCenter())));
    }

    default double getAirflowScaling() {
        final double airflow = this.getAirflow();

        if (Math.abs(airflow) <= 0.001) {
            return 1.0;
        }

        final Level level = this.getLevel();
        final Vector3d pos = JOMLConversion.toJOML(this.getBlockPos().getCenter());
        final SubLevel subLevel = Sable.HELPER.getContaining(level, this.getBlockPos());

        if (subLevel == null) {
            return 1.0;
        }

        final Vector3d velocity = Sable.HELPER.getVelocity(level, subLevel, pos, new Vector3d());
        final Vector3d thrustDirection = subLevel.logicalPose().transformNormal(JOMLConversion.atLowerCornerOf(this.getBlockDirection().getNormal()));

        return Mth.clamp((airflow + velocity.dot(thrustDirection.x, thrustDirection.y, thrustDirection.z)) / airflow, 0.0, 1.0);
    }

    // [reobf 兜底] 生产环境下继承自原版 BlockEntity 的 getLevel/getBlockPos 会被重混淆成 SRG 名，
    // 无法满足本接口的官方名抽象方法（AbstractMethodError）。改为 default + 强转：
    // dev 环境类继承方法优先覆盖 default；生产环境落到 default，体内调用点会被 reobf 正确改名。
    default Level getLevel() {
        if (this instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
            return be.getLevel();
        }
        throw new IllegalStateException("BlockEntityPropeller implementor must override getLevel(): " + this.getClass());
    }

    default BlockPos getBlockPos() {
        if (this instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
            return be.getBlockPos();
        }
        throw new IllegalStateException("BlockEntityPropeller implementor must override getBlockPos(): " + this.getClass());
    }
}

