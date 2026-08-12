package dev.ryanhcode.sable.mixin.dynamic_directional_shading;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.mixinterface.dynamic_directional_shading.ModelBlockRendererCacheExtension;
import dev.ryanhcode.sable.render.dynamic_shade.SableDynamicDirectionalShading;
import dev.ryanhcode.sable.render.dynamic_shade.SubLevelVertexConsumer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import org.joml.Quaterniondc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ModelBlockRenderer.class)
public class ModelBlockRendererMixin {

    @Shadow
    @Final
    public static ThreadLocal<ModelBlockRenderer.Cache> CACHE;

    // [1.20.1 动态方向着色] 单方块子关卡绘制期把 VertexConsumer 换成 SubLevelVertexConsumer
    // （无光照面的法线统一朝上，避免局部朝上法线被当世界朝上）。
    //
    // 说明两个标志为何不同、且确实应该不同：
    //   · ModelBlockRenderer.Cache 上的 sable$onSubLevel 只在
    //     VanillaSubLevelRenderDispatcher.renderAfterSections 里置位，
    //     覆盖的是【单方块子关卡的即时绘制】；
    //   · ThreadLocal 的 isBuildingSubLevel() 由 VanillaChunkedSubLevelRenderData.compileSections
    //     置位，覆盖的是【区块网格的烘焙】。
    // 上游 1.21.1 里烘焙路径由 SectionCompilerMixin 统一置 Cache 标志，1.20.1 没有 SectionCompiler，
    // 烘焙走的是自建的 rebuildChunkSync，所以改用 ThreadLocal —— 两者是不同阶段，不是重复实现。
    //
    // [BUG-28 诊断] 烘焙期额外挂一层「只统计不改数据」的包装，用来数每个朝向真正
    // 被写进网格的四边形数量（countOnly=true 时顶点数据原样透传，不影响画面）。
    @ModifyVariable(method = "putQuadData", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private VertexConsumer sable$modifyConsumer(final VertexConsumer value) {
        if (!SableDynamicDirectionalShading.isEnabled()) {
            return value;
        }
        if (((ModelBlockRendererCacheExtension) CACHE.get()).sable$getOnSubLevel()) {
            return new SubLevelVertexConsumer(value);
        }
        if (SableDynamicDirectionalShading.isBuildingSubLevel()) {
            return new SubLevelVertexConsumer(value, true);
        }
        return value;
    }

    // [1.20.1 动态方向着色] 方向性亮度（顶 1.0 / 底 0.5）由
    // BlockAndTintGetter.getShade(Direction, boolean)（SRG m_7717_）查
    // DIRECTIONAL_SHADE[direction.ordinal()] 算出。
    // 经 TSRG 核对：ModelBlockRenderer 内对 getShade 的调用只出现在
    // m_111001_（官方 renderModelFaceFlat，所有面的主方向性亮度在此算）。
    // 这里在该调用点用 @WrapOperation 旋转传入的 Direction，使 DIRECTIONAL_SHADE 索引世界方向，
    // 翻转后原底面自动拿到世界「朝下」的暗度（0.5）。主世界构建时 isBuildingSubLevel() 为假，原样返回。
    // 注：本项目用 MixinExtras 的 @WrapOperation（包 com.llamalad7.mixinextras...），
    // 且官方名映射下用 SRG 名 + remap=false（与工程内 50+ 处一致，加载期按运行时混淆名解析）。
    @WrapOperation(method = "m_111001_", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/BlockAndTintGetter;m_7717_(Lnet/minecraft/core/Direction;Z)F", remap = false))
    private float sable$rotateShadeDirection(final BlockAndTintGetter instance, final Direction direction, final boolean cull, final Operation<Float> original) {
        if (SableDynamicDirectionalShading.isEnabled() && SableDynamicDirectionalShading.isBuildingSubLevel()) {
            final Quaterniondc orientation = SableDynamicDirectionalShading.subLevelOrientation();
            if (orientation != null && direction != null) {
                return original.call(instance, SableDynamicDirectionalShading.rotate(direction, orientation), cull);
            }
        }
        return original.call(instance, direction, cull);
    }
}
