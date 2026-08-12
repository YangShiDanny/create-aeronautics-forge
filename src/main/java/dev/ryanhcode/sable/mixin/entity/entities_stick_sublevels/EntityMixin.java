package dev.ryanhcode.sable.mixin.entity.entities_stick_sublevels;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.EntityStickExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Allows entities to receive plot positions
 */
@Mixin(Entity.class)
public abstract class EntityMixin implements EntityStickExtension {

    @Shadow
    private Level level;

    @Shadow
    public abstract void setPos(Vec3 vec3);

    @Shadow
    public abstract void moveTo(Vec3 vec3);

    @Shadow
    public abstract void moveTo(double d, double e, double f);

    @Unique
    private Vec3 sable$plotPosition = null;

    @Inject(method = "tick", at = @At("RETURN"))
    private void sable$updateSubLevelPosition(final CallbackInfo ci) {
        final Entity self = (Entity) (Object) this;

        // [BUG36·真因修复] 物理化后的子层级实体（含结构图解）坐标位于 plot 空间（约 2.04E7），
        // 第三方剔除模组 EntityCulling 的 WorldRendererMixin 在 LevelRenderer#renderEntity 的 HEAD 注入，
        // 用未变换的 plot 包围盒对「世界相机」做遮挡射线检测 → 必判遮挡 → ci.cancel() 取消整段渲染
        // （含 sable 坐标变换注入），故物理化后图解不绘制。其唯一豁免口子 ignoresCulling 读 Entity.noCulling，
        // 但 shouldRender 内（renderEntity 调用链中部）置位发生在 HEAD cancel 之后，永远来不及。
        // 因此改在每帧 tick（渲染之前）据此置位 noCulling，确保 EntityCulling 的 HEAD 检查直接放行；
        // 实体离开子层级（去物理化/下船/摘板）时复位为 false，恢复正常剔除。
        if (this.level.isClientSide && !(self instanceof final Player player && player.isLocalPlayer()) && !(self instanceof ItemEntity)) {
            final SubLevel containing = Sable.HELPER.getContaining(self);
            final SubLevel tracking = Sable.HELPER.getTrackingSubLevel(self);
            final boolean shouldNoCull = (containing != null || tracking != null);
            // [BUG36·真因修复] entityculling 字节码读 Entity.f_19811_（SRG 名，=noCulling），其唯一剔除豁免口子 ignoresCulling。
            // 不同模组映射配置下运行时字段名可能是 f_19811_ 或 noCulling，用反射双写两者，确保 entityculling 一定能读到。
            if (shouldNoCull || self.noCulling) {
                sable$setNoCulling(self, shouldNoCull);
            }
        }

        // non wompy wompy
        if (this.sable$plotPosition != null) {
            final SubLevel subLevel = Sable.HELPER.getContaining(this.level, this.sable$plotPosition);

            if (subLevel != null) {
                this.setPos(subLevel.logicalPose().transformPosition(this.sable$plotPosition));
                ((EntityMovementExtension) this).sable$setTrackingSubLevel(subLevel);
            } else {
                this.sable$plotPosition = null;
            }
        } else if (this.level.isClientSide && !(self instanceof final Player player && player.isLocalPlayer()) && !(self instanceof ItemEntity)) {
            // if we're on the client and the plot position doesn't exist, this must mean the entity was recently
            // networked out of the plot, so let's get rid of the tracking sub-level
            ((EntityMovementExtension) this).sable$setTrackingSubLevel(null);
        }
    }

    @Override
    public void sable$plotLerpTo(final Vec3 pos, final int lerpSteps) {
        this.sable$setPlotPosition(pos);
    }

    @Override
    public void sable$setPlotPosition( final Vec3 position) {
        this.sable$plotPosition = position;
    }

    @Override
    public  Vec3 sable$getPlotPosition() {
        return this.sable$plotPosition;
    }

    @Inject(method = "recreateFromPacket", at = @At("TAIL"))
    public void sable$recreateFromPacket(final ClientboundAddEntityPacket packet, final CallbackInfo ci) {
        if (!EntitySubLevelUtil.shouldKick((Entity) (Object) this)) return;

        final double packetX = packet.getX();
        final double packetY = packet.getY();
        final double packetZ = packet.getZ();

        final SubLevel packetSubLevel = Sable.HELPER.getContaining(this.level, packetX, packetZ);
        if (packetSubLevel != null) {
            final Vector3d globalPacketPos = packetSubLevel.logicalPose().transformPosition(new Vector3d(packetX, packetY, packetZ));
            this.moveTo(globalPacketPos.x, globalPacketPos.y, globalPacketPos.z);
        }
    }

    // [BUG36·真因修复] 反射双写 Entity.f_19811_（SRG，entityculling 读的字段）与 noCulling（official 源码名），
    // 绕过不同映射配置下运行时字段名不一致导致 entityculling 读不到 noCulling 的问题。哪个字段存在就写哪个。
    @Unique
    private static void sable$setNoCulling(final Entity entity, final boolean value) {
        try { java.lang.reflect.Field f = Entity.class.getDeclaredField("f_19811_"); f.setAccessible(true); f.setBoolean(entity, value); } catch (final Exception ignored) {}
        try { java.lang.reflect.Field f = Entity.class.getDeclaredField("noCulling"); f.setAccessible(true); f.setBoolean(entity, value); } catch (final Exception ignored) {}
    }
}
