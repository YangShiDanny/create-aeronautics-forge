package dev.simulated_team.simulated.content.blocks.docking_connector;

import com.mojang.serialization.MapCodec;
import dev.simulated_team.simulated.index.SimBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PairedDockingConnectorBlock extends DirectionalBlock {


    private static final VoxelShape[] SHAPES = {
            box(0.0, -16.0, 0.0, 16.0, 16.0, 16.0),
            box(0.0, 0.0, 0.0, 16.0, 32.0, 16.0),
            box(0.0, 0.0, -16.0, 16.0, 16.0, 16.0),
            box(0.0, 0.0, 0.0, 16.0, 16.0, 32.0),
            box(-16.0, 0.0, 0.0, 16.0, 16.0, 16.0),
            box(0.0, 0.0, 0.0, 32.0, 16.0, 16.0),
    };

    public PairedDockingConnectorBlock(final Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public  VoxelShape getShape(final  BlockState state, final  BlockGetter level, final  BlockPos pos, final  CollisionContext context) {
        return SHAPES[state.getValue(FACING).get3DDataValue()];
    }

    @Override
    public VoxelShape getBlockSupportShape(final BlockState state, final BlockGetter level, final BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    public  RenderShape getRenderShape(final  BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public  BlockState updateShape(final BlockState state, final  Direction direction, final  BlockState neighborState, final  LevelAccessor level, final  BlockPos pos, final  BlockPos neighborPos) {
        final Direction facing = state.getValue(FACING);
        if (facing != direction) {
            return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
        }

        if (neighborState.is(SimBlocks.DOCKING_CONNECTOR.get()) && neighborState.getValue(BlockStateProperties.FACING) == facing.getOpposite()) {
            return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
        }

        return Blocks.AIR.defaultBlockState();
    }

    public  void playerWillDestroy(final  Level level, final  BlockPos pos, final  BlockState state, final  Player player) {
        if (!level.isClientSide()) {
            if (player.isCreative()) {
                final BlockPos connectorPos = pos.relative(state.getValue(FACING));
                final BlockState connectorState = level.getBlockState(connectorPos);
                if (connectorState.is(SimBlocks.DOCKING_CONNECTOR.get())) {
                    level.setBlock(connectorPos, Blocks.AIR.defaultBlockState(), 3);
                }
            } else {
                dropResources(state, level, pos, null, player, player.getMainHandItem());
            }
        }

        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void playerDestroy(final  Level level, final  Player player, final  BlockPos pos, final  BlockState state,  final BlockEntity blockEntity, final  ItemStack tool) {
        super.playerDestroy(level, player, pos, Blocks.AIR.defaultBlockState(), blockEntity, tool);
    }

    @Override
    public boolean canSurvive(final BlockState state, final LevelReader level, final BlockPos pos) {
        final Direction facing = state.getValue(FACING);
        final BlockState connectorBlock = level.getBlockState(pos.relative(facing));
        return connectorBlock.is(SimBlocks.DOCKING_CONNECTOR.get()) && connectorBlock.getValue(BlockStateProperties.FACING) == facing.getOpposite() && connectorBlock.getValue(DockingConnectorBlock.POWERED);
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(FACING));
    }

    @Override
    public  BlockState getStateForPlacement(final  BlockPlaceContext context) {
        return null;
    }

    @Override
    public  BlockState rotate(final BlockState state, final Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public  BlockState mirror(final BlockState state, final Mirror mirrorIn) {
        return state.rotate(mirrorIn.getRotation(state.getValue(FACING)));
    }

    public  ItemStack getCloneItemStack(final  LevelReader level, final  BlockPos pos, final  BlockState state) {
        return SimBlocks.DOCKING_CONNECTOR.asStack();
    }


}
