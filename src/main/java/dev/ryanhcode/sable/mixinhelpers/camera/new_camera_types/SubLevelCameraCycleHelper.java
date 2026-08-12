package dev.ryanhcode.sable.mixinhelpers.camera.new_camera_types;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinterface.camera.camera_zoom.CameraZoomExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

// 子层级视角切换时的公共处理：在进入/离开子层级视角时重置镜头缩放、按子层级朝向校正玩家视线、
// 以及在聊天栏（左下角）显示提示文字。这部分逻辑原本只挂在 Minecraft.handleKeybinds() 的
// setCameraType 之后（见 MinecraftMixin.sable$pre/postCycleCameraType），但 ShoulderSurfing 会消费 F5 键、
// 使 handleKeybinds 不被触发，导致开着 SS 时提示与朝向校正丢失。因此抽成共享 helper，
// 由 MinecraftMixin（禁用 SS / 不消费 F5 的情况）与 ShoulderSurfingToggleCompatMixin（开着 SS 时接管 F5）共同调用，
// 两者互斥触发，不会重复提示。
public final class SubLevelCameraCycleHelper {

    private SubLevelCameraCycleHelper() {
    }

    // 在「本次视角切换的 setCameraType 调用之前」执行（此时 cameraType 仍是切换前的）。
    // 当即将离开 SUB_LEVEL_VIEW_UNLOCKED 时，把镜头缩放归零并按子层级朝向校正玩家视线。
    public static void onPreCycle(final Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        if (minecraft.options.getCameraType() == SableCameraTypes.SUB_LEVEL_VIEW_UNLOCKED) {
            final Camera camera = minecraft.gameRenderer.getMainCamera();
            ((CameraZoomExtension) camera).sable$setZoomAmount(0.0f);

            final SubLevel subLevel = Sable.HELPER.getTrackingOrVehicleSubLevel(minecraft.cameraEntity);
            if (subLevel != null) {
                final Vec3 globalLookDir = subLevel.logicalPose().transformNormalInverse(minecraft.player.getLookAngle());
                minecraft.player.lookAt(EntityAnchorArgument.Anchor.FEET, minecraft.player.position().add(globalLookDir));
            }
        }
    }

    // 在「本次视角切换的 setCameraType 调用之后」执行（此时 cameraType 已是切换后的）。
    // 处理「切进了子层级视角但玩家其实不在气球上」时自动跳出，并显示左下角聊天栏提示。
    public static void onPostCycle(final Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        while (minecraft.options.getCameraType() == SableCameraTypes.SUB_LEVEL_VIEW
                || minecraft.options.getCameraType() == SableCameraTypes.SUB_LEVEL_VIEW_UNLOCKED) {
            final SubLevel subLevel = Sable.HELPER.getTrackingOrVehicleSubLevel(minecraft.cameraEntity);
            if (subLevel != null) {
                break;
            }
            minecraft.options.setCameraType(minecraft.options.getCameraType().cycle());
        }

        final CameraType cameraType = minecraft.options.getCameraType();
        final LocalPlayer player = minecraft.player;

        if (cameraType == SableCameraTypes.SUB_LEVEL_VIEW) {
            player.displayClientMessage(Component.translatable("camera_type.sub_level_view").withStyle(Style.EMPTY.withColor(0xffaaaaaa)), false);
        } else if (cameraType == SableCameraTypes.SUB_LEVEL_VIEW_UNLOCKED) {
            final SubLevel subLevel = Sable.HELPER.getTrackingOrVehicleSubLevel(minecraft.cameraEntity);

            // 按子层级朝向校正玩家视线
            if (subLevel != null) {
                final Vec3 globalLookDir = subLevel.logicalPose().transformNormal(player.getLookAngle());
                player.lookAt(EntityAnchorArgument.Anchor.FEET, player.position().add(globalLookDir));
            }

            player.displayClientMessage(Component.translatable("camera_type.sub_level_view_unlocked").withStyle(Style.EMPTY.withColor(0xffaaaaaa)), false);
        }
    }
}
