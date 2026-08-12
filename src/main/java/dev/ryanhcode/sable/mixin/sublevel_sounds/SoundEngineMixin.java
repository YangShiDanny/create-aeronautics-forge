package dev.ryanhcode.sable.mixin.sublevel_sounds;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.sound.MovingSoundInstanceDelegate;
import dev.ryanhcode.sable.sound.SoundInstanceDelegated;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Iterator;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {

    @ModifyVariable(method = "play", at = @At("HEAD"), argsOnly = true)
    private SoundInstance sable$play(final SoundInstance instance) {
        final ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return instance;
        }

        final SubLevel subLevel = Sable.HELPER.getContaining(level, instance.getX(), instance.getZ());

        if (subLevel != null) {
            return new MovingSoundInstanceDelegate(instance, subLevel);
        }

        // [1.20.1 移植修复·物理化飞行] 兜底：声音坐标明明落在地块区（远离主世界原点约 2048 万格），
        // 却没能按坐标查到所属子关卡（例如该地块正处于重新登记的空档）。这时退而用
        // "玩家当前正在追踪的子关卡"来做委托，让声音跟随飞行结构。
        //
        // [BUG-29 修正] 这里必须先做坐标判定再兜底。此前的写法只要玩家在追踪任意子关卡，
        // 就把【所有】声音（含主世界脚步声、环境音）统统委托出去，等于把主世界的声音
        // 一并投影到子关卡坐标系，会造成主世界音效方位错乱。现在只兜底地块区坐标的声音。
        if (Math.abs(instance.getX()) > 1_000_000.0D || Math.abs(instance.getZ()) > 1_000_000.0D) {
            final net.minecraft.world.entity.player.Player player = Minecraft.getInstance().player;
            if (player != null) {
                final SubLevel tracking = Sable.HELPER.getTrackingSubLevel(player);
                if (tracking != null) {
                    return new MovingSoundInstanceDelegate(instance, tracking);
                }
            }
        }

        return instance;
    }

    @ModifyVariable(method = "stop(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"), argsOnly = true)
    private SoundInstance sable$stop(final SoundInstance instance) {
        if (instance instanceof final SoundInstanceDelegated delegated) {
            if (delegated.getDelegate() != null) {
                return delegated.getDelegate();
            }
        }

        return instance;
    }

    @Inject(method = "tickNonPaused()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;execute(Ljava/util/function/Consumer;)V", shift = At.Shift.AFTER, ordinal = 0), locals = LocalCapture.CAPTURE_FAILEXCEPTION)
    private void sable$tick(final CallbackInfo ci, final Iterator<TickableSoundInstance> sounds, final TickableSoundInstance sound,
                            final float volume, final float pitch, final Vec3 pos, final ChannelAccess.ChannelHandle access) {
        if (sound instanceof final MovingSoundInstanceDelegate delegated) {
            access.execute(delegated::tickWithChannel);
        }
    }

    @Inject(method = "stop(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;execute(Ljava/util/function/Consumer;)V", shift = At.Shift.AFTER), locals = LocalCapture.CAPTURE_FAILEXCEPTION)
    private void sable$clear(final SoundInstance sound, final CallbackInfo ci, final ChannelAccess.ChannelHandle access) {
        if (sound instanceof final MovingSoundInstanceDelegate delegated) {
            access.execute(delegated::unload);
        }
    }
}
