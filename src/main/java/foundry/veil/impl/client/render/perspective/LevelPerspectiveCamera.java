package foundry.veil.impl.client.render.perspective;

import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Quaternionf;
import org.joml.Vector3d;

public final class LevelPerspectiveCamera {
    private final Vector3d position = new Vector3d();
    private final Quaternionf rotation = new Quaternionf();

    public LevelPerspectiveCamera() {}

    // [1.20.1 移植] NeoForge 1.21 的 LevelPerspectiveCamera.setup 用于把摄像机放到子世界坐标并朝向给定旋转。
    // 1.20.1 无此类；这里仅保存位置/朝向，供 rotation() 取回旋转叠加到 PoseStack。
    public void setup(final Vector3d pos, final Object ignored, final ClientLevel level, final Quaternionf orientation, final float partialTicks) {
        this.position.set(pos);
        if (orientation != null) {
            this.rotation.set(orientation);
        } else {
            this.rotation.identity();
        }
    }

    public Quaternionf rotation() {
        return new Quaternionf(this.rotation);
    }
}
