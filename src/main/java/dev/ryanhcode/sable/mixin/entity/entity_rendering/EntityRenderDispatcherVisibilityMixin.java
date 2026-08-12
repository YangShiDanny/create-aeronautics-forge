package dev.ryanhcode.sable.mixin.entity.entity_rendering;

import dev.ryanhcode.sable.Sable;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * [BUG-36 真因修复] 锁定「子层级实体」的渲染可见性判定，阻断第三方剔除模组把结果改回不可见。
 *
 * <p>问题现象：气球物理化之后，挂在上面的结构图解板看不见（但仍能交互）；去物理化立刻恢复。
 *
 * <p>真因（反编译 Graphene 2.1.3 字节码实证）：
 * Graphene 的 {@code client.renderer.culling.EntityRenderDispatcherMixin} 在
 * {@code EntityRenderDispatcher#shouldRender} 的 <b>TAIL</b> 注入了
 * {@code graphene$skipCulledOrTickSkippedEntity}，逻辑为：
 * <pre>
 *   if (!cir.getReturnValue()) return;                       // 已经是 false 就不管
 *   if (EntityTickHelper.shouldSkipTick(entity)
 *       || GrapheneClient.instance.shouldSkipEntity(entity)) {
 *       cir.setReturnValue(false);                           // 把 true 改成 false
 *   }
 * </pre>
 * 物理化后子层级实体的 {@code position()} 位于 plot 空间（约 2048 万格），距玩家约两千万格，
 * 任何基于「实体与玩家距离」的剔除/跳 tick 判定都必然命中 → 返回值被改成 false →
 * {@code LevelRenderer} 的实体循环条件不成立 → {@code renderEntity} 整段不执行 →
 * sable 的坐标变换注入与实体渲染器全都拿不到调用 → 板子隐形。
 *
 * <p>为什么此前的 {@code noCulling} 修复无效：Graphene 与 EntityCulling 的
 * {@code renderEntity} 注入确实都会检查 {@code Entity#noCulling} 并放行，
 * 但 Graphene 这处 {@code shouldRender} 的 TAIL 注入 <b>完全不看 noCulling</b>，
 * 在更早的循环判定阶段就把实体拦掉了，根本轮不到 renderEntity。
 *
 * <p>修复原理：Mixin 对同一注入点按 priority 升序应用，先应用者先执行；而
 * {@code CallbackInfoReturnable#setReturnValue} 隐含 cancel，一旦 cancel，
 * 后续 handler 不再执行。Graphene 的 mixin 配置 {@code priority=2000}，
 * 故本 mixin 取 {@code priority=500}（远小于 2000）抢先执行，把内层
 * {@code EntityRenderer#shouldRender}（sable 已按 renderPose 变换后的世界坐标做过正确视锥判定）
 * 的结果原样固定下来并 cancel，Graphene 的 TAIL 注入便不会再执行。
 *
 * <p>注意：这里刻意 <b>不</b> 无条件返回 true，而是沿用内层算好的视锥判定结果，
 * 因此正常的视锥剔除依旧生效，不会白白增加渲染开销；只有真正属于子层级的实体才走这条路径。
 */
@Mixin(value = EntityRenderDispatcher.class, priority = 500)
public class EntityRenderDispatcherVisibilityMixin {

    @Inject(method = "shouldRender", at = @At("TAIL"), cancellable = true)
    private <E extends Entity> void sable$lockSubLevelVisibility(final E entity,
                                                                 final Frustum frustum,
                                                                 final double camX,
                                                                 final double camY,
                                                                 final double camZ,
                                                                 final CallbackInfoReturnable<Boolean> cir) {
        // 先查便宜的 tracking（读字段），再查按坐标查表的 containing，尽量降低每帧每实体的开销
        if (Sable.HELPER.getTrackingSubLevel(entity) == null
                && Sable.HELPER.getContainingClient(entity) == null) {
            return;
        }

        final boolean sable$visible = cir.getReturnValueZ();

        // setReturnValue 隐含 cancel：既固定住正确判定结果，又阻断后续第三方 TAIL 注入
        cir.setReturnValue(sable$visible);
    }
}
