package dev.eriksonn.aeronautics.mixin.render.vanilla;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.mojang.blaze3d.shaders.Uniform;
import dev.eriksonn.aeronautics.content.blocks.levitite.LevititeShaderManager;
import dev.eriksonn.aeronautics.index.client.AeroRenderTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

// [1.20.1 移植临时禁用] 本 Mixin 按 NeoForge 1.21 渲染管线写法：在 LevelRenderer.renderSectionLayer 内
// ShaderInstance.clear() 调用之后注入，设置 levitite 自定义着色器 time uniform 并处理 ghost 跳过。
// 但 1.20.1 的渲染管线结构不同：① 方法名是 SRG m_172993_ 而非官方名 renderSectionLayer；
// ② 方法签名为 (RenderType, PoseStack, double, double, double, Matrix4f)，第 2 参是 PoseStack、且只有一个 Matrix4f；
// ③ 方法体内根本不存在 shader.clear()（m_173363_）调用点，原 @At 注入点失效。
// 完整重写需深改 levitite 着色器注入逻辑（大工程），故暂时从 aeronautics.mixins.json 的 client 列表移除，
// 先保证游戏能进、能玩、能拉杆装配。levitite 浮空方块本身照常存在、物理照常生效，仅失去流动动画那层视觉特效。
// 待核心玩法稳定后，单独补回本 Mixin 的 1.20.1 适配版本。
@Mixin(value = LevelRenderer.class, priority = 990)
public class LevelRendererMixin {

    @Shadow
    
    private ClientLevel level;

    @Inject(remap = false, method = "renderSectionLayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ShaderInstance;m_173363_()V", shift = At.Shift.AFTER))
    public void aeronautics$setupLevititeShaders(RenderType renderType, double x, double y, double z, Matrix4f frustrumMatrix, Matrix4f projectionMatrix, CallbackInfo ci, @Local ShaderInstance shaderinstance, @Local LocalBooleanRef flag1) {
        if (renderType == AeroRenderTypes.levititeGhosts()) {
            flag1.set(false); // skip rendering
        } else if (renderType == AeroRenderTypes.levitite()) {

            Uniform time = shaderinstance.getUniform("time");
            if (time != null) {
                long ticks = this.level.getGameTime();
                final float pt = Minecraft.getInstance().getFrameTime();
                ticks = ticks % 100000;
                time.set(ticks + pt);
            }

            LevititeShaderManager.prepareShaderForWorld(shaderinstance, x, y, z);
        }
    }

    @Inject(remap = false, method = "renderSectionLayer", at = @At(value = "TAIL"))
    public void aeronautics$cleanupLevititeShaders(RenderType renderType, double x, double y, double z, Matrix4f frustrumMatrix, Matrix4f projectionMatrix, CallbackInfo ci, @Local ShaderInstance shaderinstance) {
        if (renderType == AeroRenderTypes.levitite()) {
            // reset back to world once rendering is done, for safety
            LevititeShaderManager.prepareShaderForWorld(shaderinstance, x, y, z);
        }
    }

}
