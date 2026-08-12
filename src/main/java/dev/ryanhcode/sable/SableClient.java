package dev.ryanhcode.sable;

import dev.ryanhcode.sable.debug.SableClientGizmoHandler;
import dev.ryanhcode.sable.network.client.SableClientNetworkEventLoop;
import dev.ryanhcode.sable.render.water_occlusion.WaterOcclusionRenderer;

/**
 * No-op client stub for the Forge 1.20.1 port. The real client implementation
 * (which depended on the NeoForge-only Veil rendering API) has been moved out of
 * the compiled source set into src/render/java and is not built on Forge.
 *
 * <p>The debug gizmo handler, network event loop and water-occlusion renderer
 * singletons are kept wired so the code that references them keeps compiling;
 * their render hooks are disabled on Forge 1.20.1 (see the relevant classes).
 */
public class SableClient {

    public static final SableClientGizmoHandler GIZMO_HANDLER = new SableClientGizmoHandler();
    public static final SableClientNetworkEventLoop NETWORK_EVENT_LOOP = new SableClientNetworkEventLoop();
    public static final WaterOcclusionRenderer WATER_OCCLUSION_RENDERER = new WaterOcclusionRenderer();

    public static void init() {
    }

    public static boolean useNativeTransport() {
        return false;
    }
}
