package foundry.veil.api.compat;
public final class SodiumCompat {
    private SodiumCompat() {}
    public static boolean isLoaded() {
        return net.minecraftforge.fml.ModList.get().isLoaded("sodium");
    }
}
