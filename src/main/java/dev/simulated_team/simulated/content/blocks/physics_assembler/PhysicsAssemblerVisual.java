package dev.simulated_team.simulated.content.blocks.physics_assembler;

import com.simibubi.create.content.redstone.analogLever.AnalogLeverBlock;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import dev.simulated_team.simulated.index.SimPartialModels;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

import java.util.function.Consumer;

/**
 * [1.20.1 移植修正] 物理组装器拉杆此前完全隐形。
 * 根因：SimBlockEntityTypes 里 PHYSICS_ASSEMBLER 只注册了 BlockEntityRenderer（immediate 模式），
 * 没有注册 .visual(...) 这一行 —— 而 flywheel 可视化开启时，BER 的 immediate 几何会被
 * flywheel 管线静默吞掉，且没有其他兜底，于是拉杆永远不画。
 * 油门拉杆 ThrottleLeverVisual 正是靠这个 flywheel 实例渲染器在可视化开启时把拉杆画出来的。
 * 这里完全照抄其结构，只把模型换成 SimPartialModels.ASSEMBLER_LEVER，
 * 并把角度换算对齐 PhysicsAssemblerRenderer（getClientAngle 返回角度，绕 X 轴旋转 + FACE/FACING 朝向）。
 */
public class PhysicsAssemblerVisual extends AbstractBlockEntityVisual<PhysicsAssemblerBlockEntity> implements SimpleDynamicVisual {

    private final TransformedInstance lever;

    private final AttachFace attached;
    private final Direction facing;

    public PhysicsAssemblerVisual(final VisualizationContext ctx, final PhysicsAssemblerBlockEntity blockEntity, final float partialTick) {
        super(ctx, blockEntity, partialTick);

        this.lever = this.instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(SimPartialModels.ASSEMBLER_LEVER))
                .createInstance();

        final BlockState state = blockEntity.getBlockState();
        this.attached = state.getValue(AnalogLeverBlock.FACE);
        this.facing = state.getValue(AnalogLeverBlock.FACING);

        this.transformAll(partialTick);
    }

    @Override
    public void beginFrame(final Context context) {
        this.transformAll(context.partialTick());
    }

    private void transformAll(final float partialTicks) {
        this.lever.setIdentityTransform();
        this.initialTransform(this.lever);

        // 与 PhysicsAssemblerRenderer 对齐：getClientAngle 返回角度，转弧度后绕 X 轴（EAST）旋转，
        // 支点固定在方块中心偏上的 (1/2, 7/16, 1/2)。
        final float angle = (float) Math.toRadians(this.blockEntity.getClientAngle(partialTicks));
        this.lever
                .translate(1 / 2f, 7 / 16f, 1 / 2f)
                .rotateX(angle)
                .translateBack(1 / 2f, 7 / 16f, 1 / 2f);

        this.lever.setChanged();
    }

    private void initialTransform(final TransformedInstance instance) {
        instance.translate(this.getVisualPosition());

        final float rX;
        switch (this.attached) {
            case FLOOR -> rX = 0;
            case WALL -> rX = 90;
            default -> rX = 180;
        }

        final float rY = AngleHelper.horizontalAngle(this.facing);
        instance.rotateCentered((float) (rY / 180 * Math.PI), Direction.UP);
        instance.rotateCentered((float) (rX / 180 * Math.PI), Direction.EAST);
        instance.rotateCentered(this.attached == AttachFace.CEILING ? (float) Math.PI : 0.0f, Direction.UP);
    }

    @Override
    public void collectCrumblingInstances(final Consumer<Instance> consumer) {
        consumer.accept(this.lever);
    }

    @Override
    public void updateLight(final float v) {
        this.relight(this.lever);
    }

    @Override
    protected void _delete() {
        this.lever.delete();
    }
}
