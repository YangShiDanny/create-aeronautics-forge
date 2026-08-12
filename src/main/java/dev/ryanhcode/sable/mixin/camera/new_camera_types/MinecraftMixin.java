package dev.ryanhcode.sable.mixin.camera.new_camera_types;
import net.minecraft.network.chat.Style;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.mixinhelpers.camera.new_camera_types.SableCameraTypes;
import dev.ryanhcode.sable.mixinhelpers.camera.new_camera_types.SubLevelCameraCycleHelper;
import dev.ryanhcode.sable.mixinterface.camera.camera_zoom.CameraZoomExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Shadow
    @Final
    public Options options;

    @Shadow
    
    public ClientLevel level;

    @Shadow
    
    public Entity cameraEntity;

    @Shadow
    
    public LocalPlayer player;

    @Shadow @Final public GameRenderer gameRenderer;

    @Inject(method = "handleKeybinds()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;setCameraType(Lnet/minecraft/client/CameraType;)V", shift = At.Shift.BEFORE))
    private void sable$preCycleCameraType(final CallbackInfo ci) {
        SubLevelCameraCycleHelper.onPreCycle((Minecraft) (Object) this);
    }

    @Inject(method = "handleKeybinds()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;setCameraType(Lnet/minecraft/client/CameraType;)V", shift = At.Shift.AFTER))
    public void sable$postCycleCameraType(final CallbackInfo ci) {
        SubLevelCameraCycleHelper.onPostCycle((Minecraft) (Object) this);
    }
}
