package dev.ryanhcode.sable.mixin.entity.entities_stick_sublevels;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.EntityStickExtension;
import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.packet_mixin.PacketActuallyInSubLevelExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Shadow
    private ClientLevel level;

    @WrapOperation(method = "m_6435_", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;m_6453_(DDDFFIZ)V", remap = false))
    private void sable$handleTeleportEntity(final Entity instance,
                                            final double x,
                                            final double y,
                                            final double z,
                                            final float yRot,
                                            final float xRot,
                                            final int lerpSteps,
                                            final boolean teleport,
                                            final Operation<Void> original,
                                            @Local(argsOnly = true) final ClientboundTeleportEntityPacket packet) {
        try {
            this.sable$lerp(instance, x, y, z, yRot, xRot, lerpSteps, teleport, packet instanceof final PacketActuallyInSubLevelExtension extension && extension.sable$isActuallyInSubLevel());
        } catch (final Throwable t) {
            dev.ryanhcode.sable.Sable.LOGGER.error("客户端处理传送/移动包的子关卡插值失败（已回退原版逻辑，不断开连接）：", t);
            original.call(instance, x, y, z, yRot, xRot, lerpSteps, teleport);
        }
    }

    @WrapOperation(method = "m_7865_", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;m_6453_(DDDFFIZ)V", ordinal = 0, remap = false))
    private void sable$handleMoveEntity(final Entity instance,
                                        final double x,
                                        final double y,
                                        final double z,
                                        final float yRot,
                                        final float xRot,
                                        final int lerpSteps,
                                        final boolean teleport,
                                        final Operation<Void> original,
                                        @Local(argsOnly = true) final ClientboundMoveEntityPacket packet) {
        try {
            this.sable$lerp(instance, x, y, z, yRot, xRot, lerpSteps, teleport, packet instanceof final PacketActuallyInSubLevelExtension extension && extension.sable$isActuallyInSubLevel());
        } catch (final Throwable t) {
            dev.ryanhcode.sable.Sable.LOGGER.error("客户端处理传送/移动包的子关卡插值失败（已回退原版逻辑，不断开连接）：", t);
            original.call(instance, x, y, z, yRot, xRot, lerpSteps, teleport);
        }
    }

    @Unique
    private void sable$lerp(final Entity entity,
                            final double pX,
                            final double pY,
                            final double pZ,
                            final float pYRot,
                            final float pXRot,
                            final int pLerpSteps,
                            final boolean pTeleport,
                            final boolean actuallyInSubLevel) {
        final EntityStickExtension extension = (EntityStickExtension) entity;
        Vec3 pos = new Vec3(pX, pY, pZ);

        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);
        final SubLevel subLevel = Sable.HELPER.getContaining(this.level, pos);
        final Vec3 plotPosition = extension.sable$getPlotPosition();

        if (!actuallyInSubLevel && subLevel == null && container.inBounds(BlockPos.containing(pos))) {
            return;
        }

        if (subLevel != null && !actuallyInSubLevel) {
            if (!(entity instanceof LivingEntity)) {
                pos = subLevel.logicalPose().transformPosition(pos);
                entity.lerpTo(pos.x, pos.y, pos.z, pYRot, pXRot, pLerpSteps, pTeleport);
                return;
            }

            if (plotPosition == null) {
                // just jumped on a sub-level
                extension.sable$setPlotPosition(subLevel.logicalPose().transformPositionInverse(entity.position()));
            } else {
                final SubLevel existingSubLevel = Sable.HELPER.getContaining(this.level, plotPosition);
                if (existingSubLevel != null && subLevel != existingSubLevel) {
                    final Vec3 globalPlotPos = existingSubLevel.logicalPose().transformPosition(plotPosition);
                    extension.sable$setPlotPosition(subLevel.logicalPose().transformPositionInverse(globalPlotPos));
                }
            }

            // The X/Y/Z are unused in the instance lerpTo call. This makes sure the entity rotation is lerped
            entity.lerpTo(pX, pY, pZ, pYRot, pXRot, pLerpSteps, pTeleport);

            // This does a custom position lerp
            extension.sable$plotLerpTo(pos, pLerpSteps);
        } else {
            final SubLevel existingSubLevel = Sable.HELPER.getContaining(this.level, entity.position());

            if (subLevel != null && actuallyInSubLevel && existingSubLevel != subLevel) {
                entity.setPos(subLevel.logicalPose().transformPositionInverse(entity.position()));
            } else if (existingSubLevel != null && subLevel == null) {
                entity.setPos(existingSubLevel.logicalPose().transformPosition(entity.position()));
            }

            entity.lerpTo(pX, pY, pZ, pYRot, pXRot, pLerpSteps, pTeleport);
            extension.sable$setPlotPosition(null);
        }
    }
}