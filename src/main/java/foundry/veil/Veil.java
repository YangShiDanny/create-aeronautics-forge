package foundry.veil;
public final class Veil {
    /**
     * Backport shim flag. On Forge 1.20.1 there is no Iris (it is a Fabric mod;
     * Forge uses Oculus/Embeddium). The physics staff renderer reads this to decide
     * whether shader-based glow batches are active; false keeps the vanilla fallback.
     */
    public static final boolean IRIS = false;

    private Veil() {}
}
