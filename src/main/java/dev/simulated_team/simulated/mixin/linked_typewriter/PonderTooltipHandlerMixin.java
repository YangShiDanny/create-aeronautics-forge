package dev.simulated_team.simulated.mixin.linked_typewriter;

import net.createmod.ponder.foundation.PonderTooltipHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PonderTooltipHandler.class)
public class PonderTooltipHandlerMixin {
    // todo have create fix this
    @Redirect(remap = false, method = "makeProgressBar", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/MutableComponent;m_130940_(Lnet/minecraft/ChatFormatting;)Lnet/minecraft/network/chat/MutableComponent;", ordinal = 0))
    private static MutableComponent dontBreakMutabilityContracts(final MutableComponent instance, final ChatFormatting format) {
        return instance.copy().withStyle(format);
    }
}
