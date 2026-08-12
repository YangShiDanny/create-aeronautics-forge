package dev.simulated_team.simulated.content.blocks.lasers;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.extensions.IForgeBlockEntity;

public abstract class AbstractLaserBlockEntity extends SmartBlockEntity {
    public AbstractLaserBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    public abstract Direction getDirection();

    public Vec3i getNormal() {
        return this.getDirection().getNormal();
    }

    public abstract float getRaycastLength();

    public abstract boolean shouldCast();

    public Couple<Vec3> gatherStartAndEnd() {
        final Vec3i normal = this.getNormal();

        final Vec3 start = Vec3.atCenterOf(this.worldPosition).add(Vec3.atLowerCornerOf(normal).scale(0.5f));
        final Vec3 end = start.add(Vec3.atLowerCornerOf(normal).scale(this.getRaycastLength()));
        return Couple.create(start, end);
    }

    /**
     * [BUG-30·第三十二轮·根因修复] 返回 Forge 的<b>无限包围盒</b>，让激光对一切视锥剔除免疫。
     *
     * <p><b>为什么原来那个「沿发射方向展开射程」的盒不够用。</b>
     * 它本身算得没错，但它是<b>有限</b>盒，于是命运就交到了「谁来做剔除、用哪套坐标」手上，
     * 而这条链路在本项目里同时被三方改写过，任何一环错位，光束就整条消失：
     * <ul>
     *   <li>Forge 1.20.1 给 {@code LevelRenderer} 的全局方块实体循环加了原版没有的剔除补丁
     *       （{@code getRenderBoundingBox() → Frustum.isVisible()}，不可见直接跳过整个渲染调用）；</li>
     *   <li>子层级里这个盒是<b>局部坐标</b>（百万格量级），拿去和世界坐标的视锥比较，
     *       结果随相机角度随机错乱 —— 这正是「只有视野里没发射器才有光」的老症状；</li>
     *   <li>玩家实际装了 Embeddium / Rubidium，原版那两条方块实体循环<b>压根不执行</b>
     *       （2026-08-06 21:35 日志实证：{@code [BUG30·路径2]}、{@code [BUG30·盒]} 两条
     *       无节流探针零命中，而同一帧的子层级调度探针照常输出）。
     *       于是本模组此前在原版调用点上补的「{@code shouldRenderOffScreen} 短路」
     *       在真实环境里<b>一次都没生效过</b>，剔除完全由第三方渲染器按它自己的规则做。</li>
     * </ul>
     *
     * <p><b>为什么无限盒能一次解决。</b>
     * Forge 的 {@code isBlockEntityRendererVisible} 对 {@code INFINITE_EXTENT_AABB} 有
     * <b>引用相等的快速放行通道</b>，直接 return true；即便某个第三方渲染器绕过该钩子、
     * 自己调 {@code frustum.isVisible(box)}，无限盒也必然与视锥相交。
     * 也就是说，无论剔除发生在原版、Forge、Embeddium 还是本模组的子层级路径上，结果都是放行。
     * 这同时让「局部坐标 vs 世界坐标」的坐标空间错位问题彻底失去意义 —— 无限盒没有坐标空间。
     *
     * <p>这也正是原版信标光柱一类「渲染范围远超方块本身」的方块实体的标准做法，
     * 与 {@code AbstractLaserRenderer.shouldRenderOffScreen() == true} 的设计意图完全一致：
     * 光束长达数十格，发射器方块本身是否在屏幕内，本就不该决定光束画不画。
     *
     * <p>性能上无需担心：免除的只是「剔除判定」，实际是否画光束仍由 {@code shouldCast()} 决定，
     * 且场上激光数量以个位数计。
     */
    @Override
    public AABB getRenderBoundingBox() {
        return IForgeBlockEntity.INFINITE_EXTENT_AABB;
    }
}
