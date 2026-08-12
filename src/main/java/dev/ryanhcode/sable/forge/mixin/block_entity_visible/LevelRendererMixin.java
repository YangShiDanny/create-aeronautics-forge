package dev.ryanhcode.sable.forge.mixin.block_entity_visible;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashSet;
import java.util.Set;

@Mixin(value = LevelRenderer.class, priority = 2000)
public class LevelRendererMixin {

    /** 已经上报过的残留方块实体坐标，避免每帧刷屏。 */
    @Unique
    private static final Set<Long> sable$reportedStaleBlockEntities = new HashSet<>();

    /**
     * @author RyanH
     * @reason Take sub-levels into account for visibility check
     */
    @SuppressWarnings("unchecked")
    @Redirect(remap = false, method = "m_109599_(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;isBlockEntityRendererVisible(Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/client/renderer/culling/Frustum;)Z"), require = 0)
    private static <T extends BlockEntity> boolean isBlockEntityRendererVisible(final BlockEntityRenderDispatcher dispatcher, final BlockEntity blockEntity, final Frustum frustum) {
        final BlockEntityRenderer<T> renderer = (BlockEntityRenderer<T>) dispatcher.getRenderer(blockEntity);

        if (renderer == null) return false;

        // [子层级装配残留·渲染防护] 拆解/装配把方块挪到主世界锚点 (x=1,y=-59,z=5) 时，
        // 锚点处残留的悬空方块实体（其真实方块已被改成 air / adjustable_burner 等）会被主世界
        // 逐区块循环按固定锚点坐标画出；气球升空时真身随子层级上移、幽灵留在锚点下方，
        // 表现为「拉杆 / 辉光管数字向下偏移几格」。此类「方块实体类型与所在真实方块不匹配」
        // 的残留实体本就非法，直接跳过渲染即可消除可见偏移（不影响任何合法方块实体）。
        final Level level = blockEntity.getLevel();
        if (level != null) {
            final BlockState actual = level.getBlockState(blockEntity.getBlockPos());
            if (!blockEntity.getType().isValid(actual)) {
                if (sable$reportedStaleBlockEntities.add(blockEntity.getBlockPos().asLong())) {
                    Sable.LOGGER.info("[幽灵防护] 跳过残留方块实体渲染 {} @ {} 当前方块={}",
                            BlockEntityType.getKey(blockEntity.getType()), blockEntity.getBlockPos(), actual);
                }
                return false;
            }
        }

        // [BUG-30·第二十九轮·根因修复] 补回 Forge 原版 isBlockEntityRendererVisible 的 shouldRenderOffScreen 短路。
        // 原版实现语义为 `renderer.shouldRenderOffScreen(be) || frustum.isVisible(bb)`，
        // 即「渲染器声明『不随视锥剔除』时（如激光 AbstractLaserRenderer.shouldRenderOffScreen()=true）应永远可见」。
        // 本 mixin 重定向时漏掉了这半句，只剩带子关卡变换的 frustum.isVisible，
        // 导致激光 BE 被「坐标空间错乱的视锥剔除」随机干掉：子关卡里 getRenderBoundingBox 是局部坐标，
        // 它沿发射方向展开 range 后的中心经 getContainingClient 查询常返回 null、不做世界变换，
        // 于是拿局部坐标盒去和世界坐标 frustum 比较，结果随相机角度随机错乱 ——
        // 正是用户那句「只有视野里没指示器才有光」（某些角度误判为可见）。
        // 补回短路后，shouldRenderOffScreen=true 的激光 BE 绕过剔除、任何视角都渲染，落实设计意图。
        // 只对显式声明不剔除的渲染器生效（当前仅激光），其余 BE 仍走原有子关卡视锥逻辑，无副作用。
        if (renderer.shouldRenderOffScreen((T) blockEntity)) {
            return true;
        }

        AABB renderBounds = blockEntity.getRenderBoundingBox();

        final SubLevel subLevel = Sable.HELPER.getContainingClient(renderBounds.getCenter());

        if (subLevel != null) {
            final BoundingBox3d bb = new BoundingBox3d(renderBounds);
            renderBounds = bb.transform(subLevel.logicalPose(), bb).toMojang();
        }

        return frustum.isVisible(renderBounds);
    }
}
