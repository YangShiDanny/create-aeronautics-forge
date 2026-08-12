package dev.ryanhcode.sable.mixin.entity.entity_rendering;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Shadow
    @Final
    protected EntityRenderDispatcher entityRenderDispatcher;

    // [BUG36·真因修复] 记录「被 sable 强制置位 noCulling 的实体 ID」，供实体离开子层级后复位。
    // 用 JDK 并发集合，不在 mixin 包内定义任何自定义类（避免 IllegalClassLoadError）。
    @Unique
    private static final java.util.Set<Integer> sable$forcedNoCulling = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @ModifyReturnValue(method = "getPackedLightCoords(Lnet/minecraft/world/entity/Entity;F)I", at = @At("RETURN"))
    public final int getPackedLightCoords(final int original, final Entity arg, final float f) {
        final Vec3 lightProbeOffset = arg.getLightProbePosition(f).subtract(arg.getEyePosition(f));
        final Vector3d lightProbePosition = JOMLConversion.toJOML(Sable.HELPER.getEyePositionInterpolated(arg, f)).add(lightProbeOffset.x, lightProbeOffset.y, lightProbeOffset.z);
        final BlockPos blockpos = BlockPos.containing(lightProbePosition.x, lightProbePosition.y, lightProbePosition.z);
        final int sable$packed = LightTexture.pack(sable$getSubLevelAccountedBlockLight(original, arg.level(), LightLayer.BLOCK, blockpos, lightProbePosition),
                sable$getSubLevelAccountedSkyLight(original, arg.level(), LightLayer.SKY, blockpos, lightProbePosition));

        return sable$packed;
    }

    @Redirect(method = "getSkyLightLevel(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;)I", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBrightness(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/BlockPos;)I"))
    private int sable$getSkyLightLevel(final Level instance, final LightLayer lightLayer, final BlockPos blockPos) {
        return sable$getSubLevelAccountedSkyLight(-1, instance, lightLayer, blockPos, JOMLConversion.atCenterOf(blockPos));
    }

    @Unique
    private static int sable$getSubLevelAccountedSkyLight(final int original, final Level instance, final LightLayer lightLayer, final BlockPos blockPos, final Vector3dc probePosition) {
        final Iterable<SubLevel> all = Sable.HELPER.getAllIntersecting(instance, new BoundingBox3d(blockPos));

        int baseBrightness = original == -1 ? instance.getBrightness(lightLayer, blockPos) : LightTexture.sky(original);
        final BlockPos.MutableBlockPos localPosition = new BlockPos.MutableBlockPos();
        final BlockPos.MutableBlockPos heightmapPos = new BlockPos.MutableBlockPos();
        final Vector3d tempProbePosition = new Vector3d();

        for (final SubLevel subLevel : all) {
            final ClientSubLevel clientSubLevel = (ClientSubLevel) subLevel;

            clientSubLevel.renderPose().transformPositionInverse(probePosition, tempProbePosition);
            localPosition.set(tempProbePosition.x, tempProbePosition.y, tempProbePosition.z);

            final Level level = subLevel.getLevel();
            heightmapPos.setWithOffset(localPosition, Direction.UP);
            final LevelPlot plot = subLevel.getPlot();
            boolean isAboveGround = false;

            while (heightmapPos.getY() >= plot.getBoundingBox().minY()) {
                if (!level.getBlockState(heightmapPos).isAir()) {
                    isAboveGround = true;
                    break;
                }

                heightmapPos.move(Direction.DOWN);
            }

            if (isAboveGround) {
                if (lightLayer == LightLayer.BLOCK) {
                    baseBrightness = Math.max(baseBrightness, level.getBrightness(lightLayer, localPosition));
                } else if (lightLayer == LightLayer.SKY) {
                    final int brightness = clientSubLevel.scaleSkyLight(level.getBrightness(lightLayer, localPosition));
                    baseBrightness = Math.min(baseBrightness, brightness);
                }
            }
        }

        return baseBrightness;
    }

    @Redirect(method = "getBlockLightLevel(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;)I", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBrightness(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/BlockPos;)I"))
    private int sable$getBlockLightLevel(final Level instance, final LightLayer lightLayer, final BlockPos blockPos) {
        return sable$getSubLevelAccountedBlockLight(-1, instance, lightLayer, blockPos, JOMLConversion.atCenterOf(blockPos));
    }

    @Unique
    private static int sable$getSubLevelAccountedBlockLight(final int original, final Level instance, final LightLayer lightLayer, final BlockPos blockPos, final Vector3dc lightProbePosition) {
        final Iterable<SubLevel> all = Sable.HELPER.getAllIntersecting(instance, new BoundingBox3d(blockPos).expand(2.0));

        int l = original == -1 ? instance.getBrightness(lightLayer, blockPos) : LightTexture.block(original);
        final BlockPos.MutableBlockPos probeBlockPos = new BlockPos.MutableBlockPos();
        final Vector3d tempProbePosition = new Vector3d();

        for (final SubLevel subLevel : all) {
            final ClientSubLevel clientSubLevel = (ClientSubLevel) subLevel;
            clientSubLevel.renderPose().transformPositionInverse(lightProbePosition, tempProbePosition);
            l = Math.max(l, subLevel.getLevel().getBrightness(lightLayer, probeBlockPos.set(tempProbePosition.x, tempProbePosition.y, tempProbePosition.z)));
        }
        return l;
    }

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void sable$shouldRender(final E entity, final Frustum frustum, final double pCamX, final double pCamY, final double pCamZ, final CallbackInfoReturnable<Boolean> cir) {
        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(entity);

        if (subLevel != null) {
            // [BUG36·真因] 物理化后子层级实体（含图解板 DiagramEntity）看不见、但能交互的根因：
            // 本 mixin 在 EntityRenderer#shouldRender 的 HEAD 注入，若只 setReturnValue 而不 cancel()，
            // 原方法体仍会执行、其 return 用「原版对 plot 空间包围盒（约 2048 万格）的视锥判定」覆盖掉
            // 我们设的值 → shouldRender 实际返回 false → renderEntity 跳过 renderer → 隐形。
            // 修复见下方：cir.cancel() 让 sable$vis 真正生效。
            // 另外为子层级实体置 noCulling 作安全网（同时豁免第三方剔除模组 EntityCulling，其
            // ignoresCulling 直接读 Entity.noCulling 字段），离开子层级时复位（见下方）。
            if (!entity.noCulling) {
                entity.noCulling = true;
                sable$forcedNoCulling.add(entity.getId());
            }

            // 子层级实体 position() 在 plot 空间（约 2048 万格），经 renderPose 变换成渲染坐标（约 384 附近），
            // 用变换后的世界坐标 AABB 做视锥判定（正确），而非原版对 plot 坐标的判定（恒 false）。
            final Vector3d localPos = JOMLConversion.toJOML(entity.position());
            subLevel.renderPose().transformPosition(localPos); // in-place 变换成渲染坐标
            final AABB aabb = new AABB(localPos.x - 2.0D, localPos.y - 2.0D, localPos.z - 2.0D,
                    localPos.x + 2.0D, localPos.y + 2.0D, localPos.z + 2.0D);

            final boolean sable$vis = frustum.isVisible(aabb);
            // [BUG36·真因修复] 关键：@Inject(at=HEAD, cancellable=true) 下只 setReturnValue 而不 cancel，
            // 原方法体仍会执行、其 return 会覆盖掉我们设的值，导致 shouldRender 实际返回原版对 plot
            // 坐标（约 2048 万格）判定的 false → 物理化后实体不渲染。必须 cir.cancel() 让返回值生效。
            cir.setReturnValue(sable$vis);
            cir.cancel();

            return;
        }

        // [BUG36·真因修复] 实体已离开子层级（去物理化、下船、板子被摘下等）：把上面强制置位的
        // noCulling 复位，避免它永久跳过剔除白白吃性能。isEmpty() 先短路，没有子层级实体时零开销。
        if (!sable$forcedNoCulling.isEmpty() && sable$forcedNoCulling.remove(entity.getId())) {
            entity.noCulling = false;
        }

        if (entity.noCulling) {
            cir.setReturnValue(true);
            cir.cancel();
            return;
        }

        // on fast moving sub-levels
        final SubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel(entity);

        if (trackingSubLevel != null) {
            final float pt = Minecraft.getInstance().getPartialTick();
            final Vec3 positionInterpolated = Sable.HELPER.getEyePositionInterpolated(entity, pt)
                    .subtract(0.0, entity.getEyeHeight(), 0.0);


            AABB aABB = entity.getBoundingBoxForCulling().inflate(0.5);
            if (aABB.hasNaN() || aABB.getSize() == 0.0) {
                aABB = new AABB(entity.getX() - 2.0, entity.getY() - 2.0, entity.getZ() - 2.0, entity.getX() + 2.0, entity.getY() + 2.0, entity.getZ() + 2.0);
            }

            aABB = aABB.move(positionInterpolated.subtract(entity.position()));

            if (frustum.isVisible(aABB)) {
                cir.setReturnValue(true);
            } else {
                if (entity instanceof Mob mob && mob.isLeashed()) {
                    final Entity entity2 = mob.getLeashHolder();
                    if (entity2 != null) {
                        cir.setReturnValue(frustum.isVisible(entity2.getBoundingBoxForCulling()));
                        return;
                    }
                }

                cir.setReturnValue(false);
            }
        }
    }
}
