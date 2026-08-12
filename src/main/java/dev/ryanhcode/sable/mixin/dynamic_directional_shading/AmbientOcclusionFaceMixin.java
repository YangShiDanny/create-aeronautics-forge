package dev.ryanhcode.sable.mixin.dynamic_directional_shading;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.render.dynamic_shade.SableDynamicDirectionalShading;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import org.joml.Quaterniondc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * [1.20.1 动态方向着色] 把子关卡 AO 面（环境光遮蔽）的方向性亮度，
 * 按子关卡世界朝向旋转到世界方向。
 *
 * <p>1.20.1 的方向性亮度（顶 1.0 / 底 0.5）由 BlockAndTintGetter.getShade(Direction, boolean)
 * （SRG m_7717_）查 DIRECTIONAL_SHADE[direction.ordinal()] 算出。
 * 经 TSRG 核对：ModelBlockRenderer 内对 getShade 的调用除主路径 m_111001_
 * （官方 renderModelFaceFlat）外，也在 AO 计算 m_111167_（官方 AmbientOcclusionFace.calculate）内
 * 出现一次，二者都把收到的 direction 传给 getShade 算方向性亮度。
 *
 * <p>这里在 m_111167_ 的 getShade 调用点用 @WrapOperation 旋转 Direction 参数（与
 * ModelBlockRendererMixin 对 m_111001_ 的处理完全一致），保证翻转后 AO 也跟随世界朝向。
 * 主世界构建时 isBuildingSubLevel() 为假，原样返回。
 * 注：用 MixinExtras 的 @WrapOperation + SRG 名 + remap=false（同工程其它 mixin）。
 */
@Mixin(ModelBlockRenderer.AmbientOcclusionFace.class)
public class AmbientOcclusionFaceMixin {
    @WrapOperation(method = "m_111167_", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/BlockAndTintGetter;m_7717_(Lnet/minecraft/core/Direction;Z)F", remap = false))
    private float sable$rotateDirection(final BlockAndTintGetter instance, final Direction direction, final boolean cull, final Operation<Float> original) {
        if (SableDynamicDirectionalShading.isEnabled() && SableDynamicDirectionalShading.isBuildingSubLevel()) {
            final Quaterniondc orientation = SableDynamicDirectionalShading.subLevelOrientation();
            if (orientation != null && direction != null) {
                final Direction rotated = SableDynamicDirectionalShading.rotate(direction, orientation);
                return original.call(instance, rotated, cull);
            }
        }
        return original.call(instance, direction, cull);
    }
}
