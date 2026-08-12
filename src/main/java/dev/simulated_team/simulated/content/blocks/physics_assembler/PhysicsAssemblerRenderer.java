package dev.simulated_team.simulated.content.blocks.physics_assembler;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.redstone.analogLever.AnalogLeverBlock;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.ryanhcode.sable.Sable;
import dev.simulated_team.simulated.index.SimPartialModels;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.SuperBufferFactory;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.catnip.render.SuperByteBufferCache;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

public class PhysicsAssemblerRenderer extends SmartBlockEntityRenderer<PhysicsAssemblerBlockEntity> {

    // 用缓存区间 + SuperBufferFactory.createForBlock(AIR, ...) 现烤，
    // 与转向盘（SteeringWheelRenderer）一致；刻意绕过 CachedBuffers.partial(model, blockState)。
    public static final SuperByteBufferCache.Compartment<Boolean> ASSEMBLER_LEVER_BUFFER = new SuperByteBufferCache.Compartment<>();

    private static int PROBE_TICK = 0;

    public PhysicsAssemblerRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(final PhysicsAssemblerBlockEntity be, final float partialTicks, final PoseStack ms, final MultiBufferSource buffer, final int light, final int overlay) {
        // 本环境 flywheel 实例着色器编译失败（latest.log 中 ShaderException），
        // 实例渲染器(Visual) 画不出拉杆，且 supportsVisualization 会间歇跳变；
        // 因此强制由本 BER（立即模式 renderInto）单路径绘制拉杆，
        // 不再依赖 flywheel 的 viz 状态，普通世界与子关卡均生效、无双绘闪烁。

        final net.minecraft.client.resources.model.BakedModel baked = SimPartialModels.ASSEMBLER_LEVER.get();
        if (baked == null) {
            return;
        }

        final BlockState blockState = be.getBlockState();
        final float angle = getRenderAngle(be, partialTicks);

        PROBE_TICK++;
        if (PROBE_TICK % 120 == 0) {
            try {
                final AttachFace face = blockState.getValue(AnalogLeverBlock.FACE);
                final Direction facing = blockState.getValue(AnalogLeverBlock.FACING);
            } catch (final Throwable t) {
            }
        }

        final VertexConsumer vb = buffer.getBuffer(RenderType.solid());

        // 真拉杆：face/facing 定向 + 支点旋转。
        final SuperByteBuffer handle = SuperByteBufferCache.getInstance().get(ASSEMBLER_LEVER_BUFFER, true, () ->
                SuperBufferFactory.getInstance().createForBlock(baked, Blocks.AIR.defaultBlockState(), new PoseStack()));
        this.transform(handle, blockState)
                .translate(1 / 2f, 7 / 16f, 1 / 2f)
                .rotate(angle, Direction.EAST)
                .translate(-1 / 2f, -7 / 16f, -1 / 2f);
        handle.light(light).renderInto(ms, vb);
    }

    public static float getRenderAngle(final PhysicsAssemblerBlockEntity be, final float partialTicks) {
        if (!be.isVirtual()) {
            be.initializeLeverPosition();
        }
        return (float) Math.toRadians(be.getClientAngle(partialTicks));
    }

    private SuperByteBuffer transform(final SuperByteBuffer buffer, final BlockState leverState) {
        final AttachFace face = leverState.getValue(AnalogLeverBlock.FACE);
        final float rX = face == AttachFace.FLOOR ? 0 : face == AttachFace.WALL ? 90 : 180;
        final float rY = AngleHelper.horizontalAngle(leverState.getValue(AnalogLeverBlock.FACING));
        buffer.rotateCentered((float) (rY / 180 * Math.PI), Direction.UP);
        buffer.rotateCentered((float) (rX / 180 * Math.PI), Direction.EAST);
        return buffer;
    }
}
