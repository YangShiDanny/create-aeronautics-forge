package dev.simulated_team.simulated.index;

import dev.simulated_team.simulated.content.blocks.nav_table.navigation_target.NavigationTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import dev.simulated_team.simulated.libs.minecraft.core.component.DataComponentType;

import java.util.UUID;

public class SimDataComponents {
    public static final DataComponentType<BlockPos> ROPE_FIRST_CONNECTION =
            DataComponentType.of(BlockPos.CODEC, "rope_first_connection");

    public static final DataComponentType<UUID> LODESTONE_COMPASS_SUBLEVEL_TRACKER =
            DataComponentType.of(UUIDUtil.CODEC, "lodestone_compass_tracker");

    public static final DataComponentType<UUID> COMPASS_PLACER_UUID =
            DataComponentType.of(UUIDUtil.STRING_CODEC, "compass_placer");

    public static final DataComponentType<GlobalPos> LAST_PLAYER_DEATH_LOCATION =
            DataComponentType.of(GlobalPos.CODEC, "last_player_death_location");

    // [1.20.1 port] TARGET 依赖自定义注册表 NAVIGATION_TARGET，该注册表在 NewRegistryEvent 的
    // onFill 回调里才被赋值（CONSTRUCT 阶段仍为 null）。不能像 NeoForge 1.21 那样在静态块直接
    // byNameCodec()，否则构造阶段 NPE。改为非 final，在 register()（须于 NewRegistryEvent 之后
    // 调用，见 SimulatedNeoForge）里惰性赋值。
    public static DataComponentType<NavigationTarget> TARGET;

    public static void register() {
        if (TARGET == null) {
            TARGET = DataComponentType.of(SimRegistries.NAVIGATION_TARGET.byNameCodec(), "target");
        }
    }
}
