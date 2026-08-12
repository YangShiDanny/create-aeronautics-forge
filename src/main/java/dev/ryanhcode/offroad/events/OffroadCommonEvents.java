package dev.ryanhcode.offroad.events;

import com.simibubi.create.AllBlocks;
import dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity;
import dev.ryanhcode.offroad.content.components.TireLike;
import dev.ryanhcode.offroad.handlers.client.MultiMiningClientHandler;
import dev.ryanhcode.offroad.handlers.server.MultiMiningServerManager;
import dev.ryanhcode.offroad.index.OffroadDataComponents;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.libs.minecraft.core.component.DataComponentPatch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class OffroadCommonEvents {

    public static void physicsTick(final SubLevelPhysicsSystem physicsSystem, final double timeStep) {
        final ServerLevel level = physicsSystem.getLevel();
        WheelMountBlockEntity.applyAllBatchedForces(level, timeStep);
    }

    public static void tickLevelEvent(final Level level) {
        if (!level.isClientSide) {
            MultiMiningServerManager.tick(level);
        } else {
            MultiMiningClientHandler.tick(level);
        }
    }
}
