package dev.ryanhcode.offroad.content.items.tire;

import dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlock;
import dev.simulated_team.simulated.util.SimColors;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import org.jetbrains.annotations.NotNull;

public class TireItem extends Item {
    public TireItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(final  UseOnContext context) {
        final Player player = context.getPlayer();

        // [1.20.1 修复] 只有当玩家点击的方块【不是车架悬架】时才提示"需放在车架悬架上"。
        // 说明：右键方块时 WheelMountBlock.use 会先执行，装配成功会提前返回 CONSUME/SUCCESS，
        // 此处 useOn 根本不会触发。原先无条件弹提示，导致玩家点车架悬架的错误面(或装配成功后)
        // 也被误弹"需放在车架悬架"，产生"放不上去"的错觉。现改为仅在目标方块非车架悬架时提示。
        final boolean clickedWheelMount = context.getLevel()
                .getBlockState(context.getClickedPos())
                .getBlock() instanceof WheelMountBlock;

        if (player != null && player.level().isClientSide && !clickedWheelMount) {
            player.displayClientMessage(Component.translatable("item.offroad.tire.placement_error")
                    .setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(SimColors.NUH_UH_RED)), true);
        }

        return super.useOn(context);
    }
}
