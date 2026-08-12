package dev.simulated_team.simulated.content.blocks.lasers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.compatibility.SableShaderCompat;
import dev.ryanhcode.sable.util.SableDistUtil;
import dev.simulated_team.simulated.index.SimRenderTypes;
import net.createmod.catnip.data.Couple;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public abstract class AbstractLaserRenderer<T extends AbstractLaserBlockEntity> extends SmartBlockEntityRenderer<T> {
    public AbstractLaserRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(final T blockEntity, final float partialTicks, final PoseStack pose, final MultiBufferSource buffer, final int light, final int overlay) {
        super.renderSafe(blockEntity, partialTicks, pose, buffer, light, overlay);

        final LaserBehaviour laser = blockEntity.getAllBehaviours().stream().filter(behaviour -> behaviour instanceof LaserBehaviour).map(behaviour -> (LaserBehaviour) behaviour).findFirst().orElse(null);

        if (laser == null) {
            return;
        }

        final boolean sable$shouldCast = laser.shouldCast();
        if (sable$shouldCast) {
            final Vector4f colors = this.getColors(blockEntity, partialTicks);

            if (colors.w > 0) {
                pose.pushPose();

                this.transformPose(blockEntity, laser, pose);
                final float distance = this.getLaserLength(laser);
                this.createLaser(colors, pose, buffer, laser.getRange(), distance);

                pose.popPose();
            }
        }
    }

    public abstract Vector4f getColors(T blockEntity, float partialTicks);

    public float getLaserLength(final LaserBehaviour laser) {
        float laserRange = laser.getRange();

        final HitResult hr = this.getRenderedHitResult(laser);
        final Couple<Vec3> positions = laser.getLaserPositions().get();
        if (hr != null && !hr.getType().equals(HitResult.Type.MISS)) {
            Vec3 hitPos = hr.getLocation();
            if (laser.getVirtualHitPos() != Vec3.ZERO) {
                hitPos = laser.getVirtualHitPos();
            }

            laserRange = (float) Math.sqrt(Sable.HELPER.distanceSquaredWithSubLevels(SableDistUtil.getClientLevel(), positions.getFirst(), hitPos)) - 0.1f;
        } else if (laser.getVirtualHitPos() != Vec3.ZERO) {
            final Vec3 hitPos = laser.getVirtualHitPos();

            laserRange = (float) Math.sqrt(Sable.HELPER.distanceSquaredWithSubLevels(SableDistUtil.getClientLevel(), positions.getFirst(), hitPos)) - 0.1f;
        }

        return laserRange;
    }

    public abstract float getLaserScale(final LaserBehaviour laser);

    public HitResult getRenderedHitResult(final LaserBehaviour laser) {
        return laser.getClosestHitResult();
    }

    protected void transformPose(final T blockEntity, final LaserBehaviour laser, final PoseStack pose) {
        final Direction facing = blockEntity.getDirection();

        pose.translate(0.5, 0.5, 0.5);

        // [BUG-30·第二十三轮] 方向语义：facing.getRotation() 把局部 +Y 转到 facing 方向，
        //       rotateXDegrees(-90) 再把 +Z 转到原来的 +Y，两步合起来 ⇒ 局部 +Z 轴 = 发射方向。
        //
        // [BUG-30·第三十三轮·恢复源版基准] 激光起点 z 平移从 0.275 改回源版 1.21.1 的 0.5-0.0625 = 0.4375。
        // 第三十一/三十二轮把 0.275 当「埋进晶体 0.09 格消除缝」的临时方案；但源版激光本就从发光晶体的
        // 出光面（距方块中心 0.4376）起，0.4375 恰好齐平晶体面 —— 既不露缝也不埋入，这才是「像原版」。
        // 配套：createLaser 近端局部 z 恢复为 0（不再用 0.0725 外推），故绝对近端 = 0.4375 = 晶体面。
        TransformStack.of(pose)
                .rotate(facing.getRotation())
                .rotateXDegrees(-90)
                .translate(0, 0, 0.4375);

        final float scale = this.getLaserScale(laser);
        pose.scale(scale, scale, 1);

        pose.translate(-0.5, -0.5, 0.0);
    }

    protected void createLaser(final Vector4f color, final PoseStack pose, final MultiBufferSource buffer, final float maxLength, final float length) {
        // [BUG-30·第三十六轮] 几何从「4/8 薄平面」改为「正方形实心箱体（square prism）」。
        // 源版 1.21.1 用 4 薄平面 + Veil bloom 糊成方形发光棒；本端口无 Veil bloom，薄平面天然合不拢（中缝）。
        // 用户要求「复刻原版方形、不要圆柱」，故用正方形实心箱体：截面是方形、本身是实心体积，
        // 任何角度都闭合、绝无中缝；配合 SimRenderTypes.laser() 的 TRANSLUCENT 半透明混合（不写深度、保留深度测试防穿墙），
        // 呈一条方形半透明光棒、远端 alpha→0 自然渐隐消失，观感等同源版减去 bloom 的方形激光，
        // 且不依赖 Veil、不冲突用户装的性能/着色器模组。
        // 远端 length+0.5 与源版一致（光束略微穿过命中点）；截面边长 0.48 ≈ 源版方形宽度。
        float red = color.x();
        float blue = color.y();
        float green = color.z();
        float alpha = color.w();
        if (alpha <= 0) {
            return;
        }

        // 半透明（TRANSLUCENT）混合下，视线主要看到最靠近相机的那个面（其余在后方被半透叠加），
        // 不再像 additive 那样近+远两面亮度恒定相加 ⇒ 自然越远越暗。
        // 因本端口无 Veil bloom（源版靠 bloom 把 0.25 的 alpha 放大成亮芯），须把 alpha 抬到源版的约 1.5 倍
        // （满功率 ≈0.375），既保持半透明光棒观感、又不至于过实；比 0.6 倍（≈0.15，太暗）更亮，
        // 比 1.8 倍（≈0.45，用户反馈略实/偏亮）更通透。
        alpha *= 1.5f;

        // [BUG-30·光影适配] 检测是否启用了光影包。Oculus 接管渲染管线后，原版的半透明排序
        // 与实心箱体 12 个三角会产生缝隙；改用专用的 LASER_SHADERS（sortOnUpload=false）
        // 并在几何上去掉远端 alpha=0 的端面，只保留 4 个侧面 + 近端面。
        final boolean sable$shaders = SableShaderCompat.areShadersActive();
        final net.minecraft.client.renderer.RenderType sable$type = SimRenderTypes.laser(sable$shaders);

        // [BUG-30·第二十六轮] 源版走 SuperRenderTypeBuffer.getLateBuffer（延迟缓冲），但本端口
        // 装的 Embeddium / Rubidium Extra 不会冲刷 Create 的 late 缓冲，导致光束在第三方渲染器下完全不绘制。
        // 改为普通 getBuffer 后 Embeddium 可正常处理，已验证可见且不穿墙。
        final VertexConsumer builder = buffer.getBuffer(sable$type);

        final float lengthFrac = length / maxLength;
        // 源版 endAlpha = alpha * (1 - lengthFrac)，由 Veil 着色器负责 taper；本端口无 bloom，
        // 但改用 TRANSLUCENT 半透明混合后，远端 alpha→0 时该面被 position_color.fsh 的 discard 整片吞掉 ⇒ 自然消失。
        // 故去掉之前 0.06 的下限，让远端真实趋近 0（不再有「亮度都一致」的死亮远端，恢复「越远越暗直至消失」）。
        final float endAlpha = alpha * (1f - lengthFrac);
        // [BUG-30·第三十六轮·改为方形实心箱体] 用户要求「复刻原版方形、不要圆柱」。
        // 源版是 4 薄平面靠 Veil bloom 糊成方形发光棒；本端口无 bloom，薄平面天然合不拢（中缝）。
        // 改用正方形实心箱体（square prism）：截面是方形、本身是实心体积 ⇒ 任何角度都闭合、绝无中缝，
        // 配合 SimRenderTypes.laser() 的 TRANSLUCENT 半透明混合，呈一条方形半透明光棒，观感等同源版方形激光。
        // 截面半宽 sable$hw：本地 0.5 ⇒ 经 transformPose 的 scale(0.48) 缩放后视觉边长≈0.48 格，与原版方形一致。
        final float sable$hw = 0.5f;
        final float zN = 0f;
        final float zF = length + 0.5f;
        final float x0 = 0.5f - sable$hw, x1 = 0.5f + sable$hw;
        final float y0 = 0.5f - sable$hw, y1 = 0.5f + sable$hw;
        // 8 角点（局部坐标，+Z 为发射方向；(0,0,0) 经 transformPose 落在方块表面外 0.4375 处）
        final float[][] sable$c = new float[][]{
                { x0, y0, zN }, // 0 近 -X-Y
                { x1, y0, zN }, // 1 近 +X-Y
                { x1, y1, zN }, // 2 近 +X+Y
                { x0, y1, zN }, // 3 近 -X+Y
                { x0, y0, zF }, // 4 远 -X-Y
                { x1, y0, zF }, // 5 远 +X-Y
                { x1, y1, zF }, // 6 远 +X+Y
                { x0, y1, zF }, // 7 远 -X+Y
        };
        // [BUG-30·光影适配] 无光影时用实心箱体 12 三角，闭合最好；启用光影后
        // 改为「4 侧面 + 近端面」共 10 三角，去掉 alpha=0 的远端端面，避免光影着色器下
        // 远端面与侧面的深度/排序冲突造成缝隙。
        final int[][] sable$boxTris;
        if (sable$shaders) {
            sable$boxTris = new int[][]{
                    { 0, 4, 7 }, { 0, 7, 3 }, // 侧面(-X)
                    { 1, 2, 6 }, { 1, 6, 5 }, // 侧面(+X)
                    { 0, 3, 2 }, { 0, 2, 1 }, // 侧面(-Y)
                    { 4, 5, 1 }, { 4, 1, 0 }, // 侧面(+Y)
                    { 0, 1, 2 }, { 0, 2, 3 }, // 近端面（发射口实心）
            };
        } else {
            // 12 三角。半透明（TRANSLUCENT）混合下正确的叠放顺序是「远离相机的面先画、最靠近相机的面最后画」，
            // 故这里把远端面排在最前、近端面排在最后（NO_CULL 下绕序不影响可见性，只影响半透明混合的前后覆盖）。
            sable$boxTris = new int[][]{
                    { 4, 6, 5 }, { 4, 7, 6 }, // 远端面(+Z) 先画（远离相机）
                    { 0, 4, 7 }, { 0, 7, 3 }, // 侧面(-X)
                    { 1, 2, 6 }, { 1, 6, 5 }, // 侧面(+X)
                    { 0, 3, 2 }, { 0, 2, 1 }, // 侧面(-Y)
                    { 4, 5, 1 }, { 4, 1, 0 }, // 侧面(+Y)
                    { 0, 1, 2 }, { 0, 2, 3 }, // 近端面(-Z) 最后画（最靠近相机，半透明覆盖在最上）
            };
        }
        pose.pushPose();
        final Matrix4f sable$mat = new Matrix4f(pose.last().pose());
        for (final int[] sable$t : sable$boxTris) {
            for (int sable$vi = 0; sable$vi < 3; sable$vi++) {
                final float[] sable$p = sable$c[sable$t[sable$vi]];
                final boolean sable$isNear = sable$p[2] <= zN + 1e-3f;
                final float sable$a = sable$isNear ? alpha : endAlpha;
                // 本端口正式路径：POSITION_COLOR（无 uv/normal/uv2/overlay），与 SimRenderTypes.laser() 格式一致。
                builder.vertex(sable$mat, sable$p[0], sable$p[1], sable$p[2])
                        .color(red, green, blue, sable$a).endVertex();
            }
        }
        pose.popPose();

        // [BUG-30·第三十轮] 立即冲刷本批次：把「提交」与「绘制」锁在同一时刻，避免 endBatch 被延迟。
        if (buffer instanceof MultiBufferSource.BufferSource sable$bs) {
            final PoseStack sable$mvStack = RenderSystem.getModelViewStack();
            sable$mvStack.pushPose();
            sable$mvStack.setIdentity();
            RenderSystem.applyModelViewMatrix();
            sable$bs.endBatch(sable$type);
            sable$mvStack.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(final  T blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
