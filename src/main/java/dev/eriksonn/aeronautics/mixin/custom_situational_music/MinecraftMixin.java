package dev.eriksonn.aeronautics.mixin.custom_situational_music;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.eriksonn.aeronautics.api.CustomSituationalMusic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.Music;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import javax.annotation.Nullable;

@Mixin(Minecraft.class)
public class MinecraftMixin {

	@Shadow  public LocalPlayer player;

	@Shadow  public ClientLevel level;

	@WrapOperation(method = "m_91107_", at = @At(value = "FIELD", target = "Lnet/minecraft/sounds/Musics;f_11651_:Lnet/minecraft/sounds/Music;", opcode = 178, remap = false))
	private Music aeronautics$getSituationalMusic(Operation<Music> original) {
		Music music = original.call();
		Music customMusic = CustomSituationalMusic.getSituationalMusic(this.level, this.player);

		if(customMusic != null) {
			music = customMusic;
		}
		return music;
	}
}
