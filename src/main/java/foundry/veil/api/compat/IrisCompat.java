package foundry.veil.api.compat;
public final class IrisCompat {
    private IrisCompat() {}
    public static boolean isRenderingShadowPass() { return false; }
    public static boolean isLoaded() {
        return net.minecraftforge.fml.ModList.get().isLoaded("iris");
    }
}
