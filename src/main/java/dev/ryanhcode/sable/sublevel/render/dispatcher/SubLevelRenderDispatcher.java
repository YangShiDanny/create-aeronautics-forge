package dev.ryanhcode.sable.sublevel.render.dispatcher;

import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dispatcher abstraction for rendering sub-levels. Ported from the NeoForge 1.21.1
 * interface; method signatures use 1.20.1 Forge types.
 */
public interface SubLevelRenderDispatcher {

    /**
     * A block-entity renderer capable of both vanilla batched rendering and the
     * sable-specific single-block sub-level rendering path.
     */
    interface BlockEntityRenderer {
        BlockEntityRenderDispatcher getBlockEntityRenderDispatcher();

        void renderBlockEntities(List<BlockEntity> blockEntities, PoseStack poseStack, float partialTick, double cameraX, double cameraY, double cameraZ);

        void renderSingleBE(BlockEntity blockEntity, PoseStack poseStack, float partialTick, double cameraX, double cameraY, double cameraZ);
    }

    // [FIX] Must be a singleton: renderSectionLayer populates `singleBlockLayers`
    // and renderAfterSections consumes it in the SAME frame. A fresh instance
    // per get() made renderAfterSections see an empty set and early-return,
    // so single-block sub-levels were never drawn (invisible).
    SubLevelRenderDispatcher INSTANCE = new VanillaSubLevelRenderDispatcher();

    static SubLevelRenderDispatcher get() {
        return INSTANCE;
    }

    void onResourceManagerReload(ResourceManager resourceManager);

    SubLevelRenderData resize(ClientSubLevel subLevel, SubLevelRenderData renderData);

    SubLevelRenderData createRenderData(ClientSubLevel subLevel);

    void updateCulling(Iterable<ClientSubLevel> subLevels, double cameraX, double cameraY, double cameraZ, Frustum cullFrustum, boolean isSpectator);

    void renderSectionLayer(Iterable<ClientSubLevel> subLevels, RenderType renderType, ShaderInstance shader, double cameraX, double cameraY, double cameraZ, Matrix4f modelView, Matrix4f projection, float partialTicks);

    void renderAfterSections(Iterable<ClientSubLevel> subLevels, double cameraX, double cameraY, double cameraZ, Matrix4f modelView, Matrix4f projection, float partialTicks);

    void renderBlockEntities(Iterable<ClientSubLevel> subLevels, BlockEntityRenderer blockEntityRenderer, double cameraX, double cameraY, double cameraZ, float partialTick);

    void rebuild(Iterable<ClientSubLevel> sublevels);

    void preRenderChunks(Camera camera);

    void addDebugInfo(Consumer<String> consumer);

    void free();
}
