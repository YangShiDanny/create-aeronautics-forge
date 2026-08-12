package dev.ryanhcode.sable.compatibility;

import net.minecraft.client.renderer.ShaderInstance;

/**
 * Iris shader compatibility shim.
 *
 * <p>On Neoforge 1.21.1 this delegated to a generated
 * {@code mixinterface.compatibility.iris.ExtendedShaderExtension} to refresh
 * model matrices for sub-level rendering under Iris. That generated package is
 * unavailable on Forge 1.20.1 (Iris/Oculus use a different injection
 * model), so the method is a no-op. The public signature is preserved so the
 * sub-level render pipeline keeps compiling.
 */
public class SableIrisCompat {

    public static void refreshModelMatrices(final ShaderInstance shader) {
        // No-op on Forge 1.20.1: Iris/Oculus model-matrix refresh is handled elsewhere.
    }
}
