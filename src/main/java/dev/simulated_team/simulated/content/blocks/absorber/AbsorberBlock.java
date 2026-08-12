package dev.simulated_team.simulated.content.blocks.absorber;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import dev.simulated_team.simulated.index.SimBlockShapes;
import dev.simulated_team.simulated.index.SimSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.entity.BlockEntity;

public class AbsorberBlock extends HorizontalDirectionalBlock implements IBE<AbsorberBlockEntity>, IWrenchable {
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return getBlockEntityType().create(pos, state);
    }


    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty WET = BooleanProperty.create("wet");
    public static final DirectionProperty HORIZONTAL_FACING = BlockStateProperties.HORIZONTAL_FACING;

    public AbsorberBlock(final Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(POWERED, false).setValue(WET,false));
    }



    @Override
    public void neighborChanged(final BlockState state, final Level level, final BlockPos pos, final Block block, final BlockPos fromPos, final boolean isMoving) {
        if (!level.isClientSide) {
            final boolean flag = state.getValue(POWERED);
            if (flag != level.hasNeighborSignal(pos)) {
                level.setBlock(pos, state.cycle(POWERED), 2);
            }
        }
    }

    @Override
    public VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
        return SimBlockShapes.EVAPORATOR;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HORIZONTAL_FACING, POWERED,WET);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public InteractionResult use(final BlockState state, final Level level, final BlockPos pos, final Player player, final InteractionHand hand, final BlockHitResult hitResult) {
        final ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.CARROT) && state.getValue(POWERED)) {
            level.playLocalSound(pos, SoundEvents.GENERIC_EAT, SoundSource.BLOCKS, 0.8f, 0.9f + 0.2f * level.random.nextFloat(), false);
            level.playLocalSound(pos, SimSoundEvents.ABSORBER_EATS.event(), SoundSource.BLOCKS, 0.33f, 0.8f + 0.2f * level.random.nextFloat(), false);

            if (level instanceof final ServerLevel serverLevel) {
                final Vec3 mouthPos = pos.getCenter().add(Vec3.atLowerCornerOf(state.getValue(FACING).getNormal()).scale(0.5));
                serverLevel.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stack), mouthPos.x, mouthPos.y, mouthPos.z, 5, 0, 0.1, 0, 0.01);
            }

            stack.shrink(1);
            return InteractionResult.CONSUME;
        }
        return super.use(state, level, pos, player, hand, hitResult);
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext pContext) {
        final Direction dir = pContext.getHorizontalDirection().getOpposite();

        assert pContext.getPlayer() != null;
        return this.defaultBlockState().setValue(HORIZONTAL_FACING, pContext.getPlayer().isShiftKeyDown() ? dir.getOpposite() : dir);
    }

    @Override
    public Class<AbsorberBlockEntity> getBlockEntityClass() {
        return AbsorberBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends AbsorberBlockEntity> getBlockEntityType() {
        //return SimBlockEntityTypes.ABSORBER.get();
        return null;
    }
}

