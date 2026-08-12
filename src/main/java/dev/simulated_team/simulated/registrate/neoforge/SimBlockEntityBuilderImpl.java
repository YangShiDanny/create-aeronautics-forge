package dev.simulated_team.simulated.registrate.neoforge;

import com.tterrag.registrate.AbstractRegistrate;
import com.tterrag.registrate.builders.BlockEntityBuilder;
import com.tterrag.registrate.builders.BuilderCallback;
import com.tterrag.registrate.util.OneTimeEventReceiver;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.mixin.accessor.CreateBlockEntityBuilderAccessor;
import dev.simulated_team.simulated.registrate.SimBlockEntityBuilder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;


@SuppressWarnings("unchecked")
public class SimBlockEntityBuilderImpl<T extends BlockEntity, P> extends SimBlockEntityBuilder<T, P> {
    protected SimBlockEntityBuilderImpl(final AbstractRegistrate<?> owner, final P parent, final String name, final BuilderCallback callback, final BlockEntityFactory<T> factory) {
        super(owner, parent, name, callback, factory);
    }

    public static <T extends BlockEntity, P> BlockEntityBuilder<T, P> create(final AbstractRegistrate<?> owner, final P parent, final String name, final BuilderCallback callback, final BlockEntityBuilder.BlockEntityFactory<T> factory) {
        return new SimBlockEntityBuilderImpl(owner, parent, name, callback, factory);
    }

    @Override
    protected void registerVisualizer() {

        OneTimeEventReceiver.addModListener(Simulated.getRegistrate(), FMLClientSetupEvent.class, ($) -> {
            final NonNullSupplier<SimpleBlockEntityVisualizer.Factory<T>> visualFactory = ((CreateBlockEntityBuilderAccessor<T, P>) this).getVisualFactory();
            if (visualFactory != null) {
                        // [1.20.1 port] CreateBlockEntityBuilder.renderNormally 在 Create 6.0.8 不存在；
        // 默认从不跳过原版方块实体渲染（对应 Create 默认 renderNormally=true）。
        SimpleBlockEntityVisualizer.builder((BlockEntityType) this.getEntry()).factory(visualFactory.get()).skipVanillaRender((be) -> false).apply();
            }

        });
    }

}
