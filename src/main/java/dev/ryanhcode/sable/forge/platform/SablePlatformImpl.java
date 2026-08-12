package dev.ryanhcode.sable.forge.platform;

import dev.ryanhcode.sable.platform.SablePlatform;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.common.ForgeHooks;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class SablePlatformImpl implements SablePlatform {

    @Override
    public boolean isWrappedLevel( final Level level) {
        // phase-1: Create-compat (catnip WrappedServerLevel) is deferred to phase-2 re-port.
        return false;
    }

    @Override
    public boolean isBlockstateLadder(final BlockState state, final Level level, final BlockPos pos, final LivingEntity entity) {
        return ForgeHooks.isLivingOnLadder(state, level, pos, entity).isPresent();
    }
}
