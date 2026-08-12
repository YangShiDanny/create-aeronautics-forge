package dev.ryanhcode.sable.render.dynamic_shade;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.model.BakedQuad;

/**
 * Make all shade-less things have a normal pointing straight up for dynamic shading!
 */
public class SubLevelVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;

    /**
     * 只做统计、不改法线的模式。
     *
     * <p>本类原本是为上游（NeoForge 1.21.1 + Veil）服务的：Veil 的 GLSL 在绘制时按
     * 顶点法线实时算方向光，所以要先把「无阴影面」的法线统一掰成朝上。
     * Forge 1.20.1 的地形着色器（rendertype_solid 等）根本不读法线属性，
     * 改法线既无收益也不该在烘焙期偷偷生效。
     *
     * <p>因此 BUG-28 诊断把本类复用到烘焙期时一律置为 {@code true}：
     * 只借 {@link #putBulkData} 这个能拿到 {@link BakedQuad} 的位置统计面朝向，
     * 顶点数据原封不动透传，保证诊断本身不改变任何画面表现。
     */
    private final boolean countOnly;

    private boolean verticalNormal;

    public SubLevelVertexConsumer(final VertexConsumer delegate) {
        this(delegate, false);
    }

    public SubLevelVertexConsumer(final VertexConsumer delegate, final boolean countOnly) {
        this.delegate = delegate;
        this.countOnly = countOnly;
    }


    @Override
    public VertexConsumer vertex(final double f, final double g, final double h) {
        this.delegate.vertex(f, g, h);
        return this;
    }

    @Override
    public VertexConsumer color(final int i, final int j, final int k, final int l) {
        this.delegate.color(i, j, k, l);
        return this;
    }

    @Override
    public VertexConsumer uv(final float f, final float g) {
        this.delegate.uv(f, g);
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(final int i, final int j) {
        this.delegate.overlayCoords(i, j);
        return this;
    }

    @Override
    public VertexConsumer uv2(final int i, final int j) {
        this.delegate.uv2(i, j);
        return this;
    }

    @Override
    public void endVertex() {
        this.delegate.endVertex();
    }

    @Override
    public void defaultColor(final int r, final int g, final int b, final int a) {
        this.delegate.defaultColor(r, g, b, a);
    }

    @Override
    public void unsetDefaultColor() {
        this.delegate.unsetDefaultColor();
    }

    @Override
    public VertexConsumer normal(final float pX, final float pY, final float pZ) {
        if (this.verticalNormal) {
            this.delegate.normal(0f, 1f, 0f);
        } else {
            this.delegate.normal(pX, pY, pZ);
        }
        return this;
    }

    @Override
    public void putBulkData(final PoseStack.Pose pose, final BakedQuad bakedQuad, final float[] fs, final float f, final float g, final float h, final float i, final int[] is, final int j, final boolean bl) {
        if (this.countOnly) {
            // [BUG-28 诊断] 这里是烘焙管线上唯一既能拿到 BakedQuad、
            // 又确定会被每一个真正写进网格的四边形经过的位置，
            // 因此把「面朝向分布」统计挂在这里最准：被遮挡剔除丢掉的面根本走不到这一步。
            SableDynamicDirectionalShading.countQuad(bakedQuad.getDirection());
            // 直接把整调用透传给被包装者，而不是走 VertexConsumer 的默认逐顶点实现。
            // 原因：Forge 的 BufferBuilder 覆盖了 putBulkData，走默认实现等于绕开它，
            // 有可能让诊断本身改变画面。诊断必须零侵入，否则观察到的现象不可采信。
            this.delegate.putBulkData(pose, bakedQuad, fs, f, g, h, i, is, j, bl);
            return;
        }

        this.verticalNormal = !bakedQuad.isShade();
        VertexConsumer.super.putBulkData(pose, bakedQuad, fs, f, g, h, i, is, j, bl);
        this.verticalNormal = false;
    }

}