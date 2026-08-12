package dev.ryanhcode.sable.render.water_occlusion;

import dev.ryanhcode.sable.render.region.SimpleCulledRenderRegion;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.Collection;
import java.util.Set;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

/**
 * Manages water occlusion rendering for sub-levels.
 *
 * <p>On Neoforge 1.21.1 this used Veil's {@code AdvancedFbo} to render
 * offscreen depth buffers used to discard translucent fragments hidden behind
 * sub-level water. The Veil framebuffer API is unavailable on Forge 1.20.1,
 * so the offscreen occlusion pass is disabled here. The public API is preserved
 * so the rest of the sub-level render pipeline keeps compiling; the actual
 * occlusion effect is tracked as a TODO for a later pass.
 */
@ApiStatus.Internal
public class WaterOcclusionRenderer {
    private final Set<SimpleCulledRenderRegion> regions = new ObjectOpenHashSet<>();
    private Level level;

    private static boolean isEnabled = false;

    public static boolean isEnabled() {
        return isEnabled;
    }

    public static void setIsEnabled(final boolean isEnabled) {
        WaterOcclusionRenderer.isEnabled = isEnabled;
    }

    
    @ApiStatus.Internal
    public SimpleCulledRenderRegion addRegion(final Collection<BlockPos> blocks) {
        if (blocks.isEmpty()) {
            return null;
        }

        final SimpleCulledRenderRegion region = new WaterOcclusionRenderRegion(blocks);
        this.regions.add(region);
        return region;
    }

    public void removeRegion(final SimpleCulledRenderRegion region) {
        region.free();
        this.regions.remove(region);
    }

    public void preRenderTranslucent(final Matrix4f modelView, final Matrix4f projMat) {
        if (!isEnabled()) {
            return;
        }
        // TODO(Forge 1.20.1): re-implement offscreen depth occlusion without Veil AdvancedFbo.
    }

    public void setupTranslucentShader(final ShaderInstance shader) {
        if (!isEnabled()) {
            return;
        }
        // TODO(Forge 1.20.1): re-implement uniform/sampler binding without Veil ShaderProgram.
    }

    public void update() {
        final Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level != this.level) {
            this.level = minecraft.level;

            this.regions.forEach(SimpleCulledRenderRegion::free);
            this.regions.clear();
        }
    }
}
