package dev.simulated_team.simulated.util;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.ryanhcode.sable.MobilePlatform;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.mixinhelpers.sublevel_render.vanilla.VanillaSubLevelBlockEntityRenderer;
import dev.ryanhcode.sable.mixinterface.BlockEntityRenderDispatcherExtension;
import dev.ryanhcode.sable.forge.mixinhelper.compatibility.flywheel.SubLevelEmbedding;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.plot.ClientLevelPlot;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import dev.ryanhcode.sable.sublevel.render.dispatcher.VanillaSubLevelRenderDispatcher;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaSingleSubLevelRenderData;
import dev.simulated_team.simulated.mixin_interface.diagram.LightTextureExtension;
import dev.simulated_team.simulated.mixin_interface.diagram.VisualManagerExtension;
import dev.simulated_team.simulated.mixin_interface.diagram.VisualizationManagerExtension;
import foundry.veil.api.client.render.CameraMatrices;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.impl.client.render.perspective.LevelPerspectiveCamera;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.*;

import java.util.Collection;
import java.util.List;
import org.joml.Quaternionf;

public class SimpleSubLevelGroupRenderer {
    private static final LevelPerspectiveCamera CAMERA = new LevelPerspectiveCamera();
    private static final Matrix4f TRANSFORM = new Matrix4f();
    private static final Matrix4f BACKUP_PROJECTION = new Matrix4f();
    private static final CameraMatrices BACKUP_CAMERA_MATRICES = new CameraMatrices();
    public static boolean RENDERING_SIMPLE = false;

    /**
     * @return the chain of sub-levels that should render with a given sub-level into a diagram
     */
    public static Collection<ClientSubLevel> getRenderedChain(final ClientSubLevel subLevel) {
        final ObjectOpenHashSet<ClientSubLevel> visited = new ObjectOpenHashSet<>();
        final ObjectOpenHashSet<ClientSubLevel> frontier = new ObjectOpenHashSet<>();

        frontier.add(subLevel);

        while (!frontier.isEmpty()) {
            final ClientSubLevel current = frontier.iterator().next();

            frontier.remove(current);
            visited.add(current);

            final Iterable<SubLevel> intersecting = Sable.HELPER.getAllIntersecting(current.getLevel(), new BoundingBox3d(current.boundingBox()));

            // Intersecting dependencies
            for (final SubLevel neighbor : intersecting) {
                final ClientSubLevel serverNeighbor = (ClientSubLevel) neighbor;

                if (!visited.contains(serverNeighbor)) {
                    frontier.add(serverNeighbor);
                }
            }
        }

        return visited;
    }

    public static void renderChain(final SubLevel subLevel, final AdvancedFbo fbo, final Matrix4f modelView, final Matrix4f projectionMat, final Vector3d cameraPosition, final Quaternionf orientation, final float partialTicks) {
        final ClientSubLevel clientSubLevel = (ClientSubLevel) subLevel;
        final ClientLevel level = clientSubLevel.getLevel();
        final Collection<ClientSubLevel> subLevels = SimpleSubLevelGroupRenderer.getRenderedChain(clientSubLevel);

        renderGroup(level, subLevels, fbo, modelView, projectionMat, cameraPosition, orientation, partialTicks, true);
    }

    public static void renderGroup(final ClientLevel level, final Collection<ClientSubLevel> subLevels, final AdvancedFbo fbo, final Matrix4f modelView, final Matrix4f projectionMat, final Vector3d cameraPosition, final Quaternionf orientation, final float partialTicks, final boolean renderPlayers) {
        // Finish anything previously being rendered for safety
        final MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        bufferSource.endBatch();

        if (subLevels.isEmpty()) {
            AdvancedFbo.unbind();
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final GameRenderer gameRenderer = minecraft.gameRenderer;
        final LightTexture lightTexture = gameRenderer.lightTexture();
        final VanillaSubLevelBlockEntityRenderer beRenderer = new VanillaSubLevelBlockEntityRenderer(minecraft.getBlockEntityRenderDispatcher(), minecraft.renderBuffers(), new Long2ObjectOpenHashMap<>());

        CAMERA.setup(cameraPosition, null, minecraft.level, orientation, 0f);

        final PoseStack poseStack = new PoseStack();
        poseStack.mulPoseMatrix(TRANSFORM.set(modelView));
        poseStack.mulPose(CAMERA.rotation());

        BACKUP_PROJECTION.set(RenderSystem.getProjectionMatrix());
        gameRenderer.resetProjectionMatrix(TRANSFORM.set(projectionMat));

        final CameraMatrices matrices = VeilRenderSystem.renderer().getCameraMatrices();
        matrices.backup(BACKUP_CAMERA_MATRICES);

        final PoseStack matrix4fstack = RenderSystem.getModelViewStack();
        matrix4fstack.pushPose();
        matrix4fstack.setIdentity();
        matrix4fstack.mulPoseMatrix(poseStack.last().pose());
        RenderSystem.applyModelViewMatrix();

        final AdvancedFbo drawFbo = VeilRenderSystem.renderer().getDynamicBufferManger().getDynamicFbo(fbo);
        drawFbo.bind(true);

        // [BUG-40 修复] 离屏渲染前强制修正「颜色写入」相关的 GL 状态：
        // 本方法此前完全继承界面上下文的 GL 状态（写掩码 / 着色器颜色调制器），
        // 而上游的渲染类型或后处理可能把颜色写掩码关掉并泄漏进来；
        // 原版方块层的 setupRenderState() 在「颜色+深度都写」状态下什么都不做，不会恢复。
        // 这里强制重置为「颜色+深度都写 + 着色器颜色调制器全亮」，根除了便签离屏图只留深度、不写颜色导致的暗块。
        final float[] backupShaderColor = RenderSystem.getShaderColor().clone();
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        try {
            Lighting.setupNetherLevel(projectionMat);
            ((LightTextureExtension) lightTexture).simulated$makeDiagramLightTexture(0.65f);

            SimpleSubLevelGroupRenderer.RENDERING_SIMPLE = true;
            for (final RenderType layer : RenderType.chunkBufferLayers()) {
                layer.setupRenderState();
                final ShaderInstance shader = RenderSystem.getShader();
                // [1.20.1 移植修复] 源版此处调用 setDefaultUniforms(QUADS, modelView, projectionMat, window)
                // 把图解的正交投影矩阵写进着色器。1.20.1 没有该方法，且原版 renderChunkLayer 是手动
                // 设置 MODEL_VIEW_MATRIX / PROJECTION_MATRIX 再 apply() 的——之前直接跳过导致
                // 着色器沿用上一帧世界渲染的透视投影：图样透视畸变、且不随 plot 包围盒自适应缩放
                // （大结构超出图纸）。这里按原版 renderChunkLayer 的写法手动补齐。
                if (shader.MODEL_VIEW_MATRIX != null) {
                    shader.MODEL_VIEW_MATRIX.set(RenderSystem.getModelViewMatrix());
                }
                if (shader.PROJECTION_MATRIX != null) {
                    shader.PROJECTION_MATRIX.set(projectionMat);
                }
                shader.apply();
                SubLevelRenderDispatcher.get().renderSectionLayer(subLevels, layer, shader, cameraPosition.x, cameraPosition.y, cameraPosition.z, RenderSystem.getModelViewMatrix(), projectionMat, partialTicks);

                // single block sub-levels
                final VertexConsumer consumer = bufferSource.getBuffer(layer);

                for (final ClientSubLevel sublevel : subLevels) {
                    final SubLevelRenderData data = sublevel.getRenderData();

                    if (!(data instanceof final VanillaSingleSubLevelRenderData singleRenderData)) {
                        continue;
                    }

                    singleRenderData.renderSingleBlock(layer, consumer, modelView, cameraPosition.x, cameraPosition.y, cameraPosition.z);
                }

                bufferSource.endBatch(layer);
                shader.clear();
                layer.clearRenderState();
            }
            ((LightTextureExtension) lightTexture).simulated$makeDiagramLightTexture(1.0f);
            SimpleSubLevelGroupRenderer.RENDERING_SIMPLE = false;

            final VisualizationManager visualizationManager = VisualizationManager.get(level);

            // Render block-entities with visuals normally
            if (visualizationManager instanceof final VisualizationManagerExtension extension) {
                extension.sable$setDrawingDiagram(true);

                for (final ClientSubLevel beSubLevel : subLevels) {
                    final BlockEntityRenderDispatcherExtension dispatcher = (BlockEntityRenderDispatcherExtension) beRenderer.getBlockEntityRenderDispatcher();

                    final SubLevelEmbedding embeddingInfo = ((VisualManagerExtension) visualizationManager.blockEntities()).sable$getBEEmbeddingInfo(beSubLevel);

                    // 合并 flywheel 嵌入信息的方块实体 与 sable 原生子关卡 actors
                    // （含物理组装器拉杆等无 Visual 的方块实体）。
                    // 着色器成功时 embeddingInfo 非 null，但其 blockEntities() 不含重组装器；
                    // 着色器失败时为 null。无论哪种，都并回 sable 原生 actors，
                    // 保证子关卡内实体类方块实体（如物理化后的组装器拉杆）被 BER 渲染、不再隐形。
                    final java.util.Set<BlockEntity> merged = new java.util.LinkedHashSet<>();
                    if (embeddingInfo != null) {
                        merged.addAll(embeddingInfo.blockEntities());
                    }
                    final ClientLevelPlot plot = beSubLevel.getPlot();
                    if (plot != null) {
                        for (final BlockEntitySubLevelActor actor : plot.getBlockEntityActors()) {
                            merged.add((BlockEntity) actor);
                        }
                    }
                    if (merged.isEmpty()) {
                        continue;
                    }
                    final java.util.List<BlockEntity> blockEntities = new java.util.ArrayList<>(merged);

                    final Vector3d chunkOffset = new Vector3d();
                    final Matrix4f transformation = new Matrix4f();
                    final Matrix4f transformationInverse = new Matrix4f();

                    final SubLevelRenderData data = beSubLevel.getRenderData();

                    beSubLevel.renderPose().rotationPoint().negate(chunkOffset.zero());
                    data.getTransformation(cameraPosition.x, cameraPosition.y, cameraPosition.z, transformation);

                    final Vector3f c = transformation.invert(transformationInverse).transformPosition(new Vector3f());
                    dispatcher.sable$setCameraPosition(new Vec3(c.x - chunkOffset.x(), c.y - chunkOffset.y(), c.z - chunkOffset.z()));

                    final PoseStack beMatrices = new PoseStack();
                    beMatrices.pushPose();
                    beMatrices.mulPoseMatrix(transformation);
                    // [BUG-28 第二阶段] mulPoseMatrix 在 1.20.1 只乘 pose、不维护 normal，
                    // 这里 normal 会停在单位阵，方块实体的方向性明暗算错（详见 syncNormalMatrix 说明）。
                    dev.ryanhcode.sable.sublevel.render.dispatcher.VanillaSubLevelRenderDispatcher.syncNormalMatrix(beMatrices);
                    beRenderer.renderBlockEntities(blockEntities, beMatrices, partialTicks, -chunkOffset.x, -chunkOffset.y, -chunkOffset.z);
                    beMatrices.popPose();

                    dispatcher.sable$setCameraPosition(null);
                }
            } else {
                // [1.20.1 移植兜底] 本环境 flywheel 实例着色器编译失败
                // （见 latest.log ShaderException$Compile: flywheel_instance_transformed），
                // 导致 VisualManagerExtension 不可用 / getBEEmbeddingInfo 返回 null，
                // 上方 if 块整体被跳过 → 子关卡所有方块实体（含物理组装器拉杆）都不渲染。
                // 改用 sable 自身维护的子关卡 BlockEntity 列表（不依赖 flywheel）兜底绘制，
                // 正常环境（flywheel 可用）完全走上方 if 块，不受此分支影响。
                for (final ClientSubLevel beSubLevel : subLevels) {
                    final ClientLevelPlot plot = beSubLevel.getPlot();
                    if (plot == null) {
                        continue;
                    }

                    final java.util.List<BlockEntity> actors = new java.util.ArrayList<>();
                    for (final BlockEntitySubLevelActor actor : plot.getBlockEntityActors()) {
                        actors.add((BlockEntity) actor);
                    }
                    if (actors.isEmpty()) {
                        continue;
                    }

                    final BlockEntityRenderDispatcherExtension dispatcher =
                            (BlockEntityRenderDispatcherExtension) beRenderer.getBlockEntityRenderDispatcher();

                    final Vector3d chunkOffset = new Vector3d();
                    final Matrix4f transformation = new Matrix4f();
                    final Matrix4f transformationInverse = new Matrix4f();

                    final SubLevelRenderData data = beSubLevel.getRenderData();

                    beSubLevel.renderPose().rotationPoint().negate(chunkOffset.zero());
                    data.getTransformation(cameraPosition.x, cameraPosition.y, cameraPosition.z, transformation);

                    final Vector3f c = transformation.invert(transformationInverse).transformPosition(new Vector3f());
                    dispatcher.sable$setCameraPosition(new Vec3(c.x - chunkOffset.x(), c.y - chunkOffset.y(), c.z - chunkOffset.z()));

                    final PoseStack beMatrices = new PoseStack();
                    beMatrices.pushPose();
                    beMatrices.mulPoseMatrix(transformation);
                    // [BUG-28 第二阶段] 同上：补齐 normal 矩阵，否则图解里的可动部件明暗错乱。
                    dev.ryanhcode.sable.sublevel.render.dispatcher.VanillaSubLevelRenderDispatcher.syncNormalMatrix(beMatrices);
                    beRenderer.renderBlockEntities(actors, beMatrices, partialTicks, -chunkOffset.x(), -chunkOffset.y(), -chunkOffset.z());
                    beMatrices.popPose();

                    dispatcher.sable$setCameraPosition(null);
                }
            }

            // Render normal block-entities
            SubLevelRenderDispatcher.get().renderBlockEntities(subLevels, beRenderer, cameraPosition.x, cameraPosition.y, cameraPosition.z, partialTicks);

            // [手机端优化·B3] 图解离屏渲染的实体候选范围：源版把子关卡包围盒外扩 16 格再全表筛选，
            // 手机端外扩量降到 4 格，能显著减少 getEntitiesOfClass 的遍历与后续逐实体判定；
            // 真正位于子关卡内的实体本来就在包围盒里，缩小外扩只会漏掉「贴边悬空」的极端个例。
            final double sable$entityInflate = MobilePlatform.isMobile() ? 4.0 : 16.0;
            for (final ClientSubLevel entitySubLevel : subLevels) {
                final List<Entity> entities = level.getEntitiesOfClass(Entity.class, entitySubLevel.getPlot().getBoundingBox().toAABB().inflate(sable$entityInflate));

                final PoseStack entityPoseStack = new PoseStack();
                entityPoseStack.pushPose();
                entityPoseStack.mulPoseMatrix(TRANSFORM.set(modelView));

                for (final Entity entity : entities) {
                    if (Sable.HELPER.getContaining(entity) != entitySubLevel && Sable.HELPER.getTrackingOrVehicleSubLevel(entity) != entitySubLevel) {
                        continue;
                    }

                    if (!renderPlayers && entity instanceof Player) {
                        continue;
                    }

                    final float partialTick = minecraft.getFrameTime();

                    final EntityRenderDispatcher erd = minecraft.getEntityRenderDispatcher();
                    final int packedLight = erd.getPackedLightCoords(entity, partialTick);
                    erd.render(entity, cameraPosition.x, cameraPosition.y, cameraPosition.z, 0f, partialTick, entityPoseStack, bufferSource, packedLight);
                }
                entityPoseStack.popPose();
            }

            if (visualizationManager instanceof final VisualizationManagerExtension extension) {
                extension.sable$setDrawingDiagram(false);
            }

            bufferSource.endBatch();
        } finally {
            // BUG-40：把着色器颜色调制器还原成进入时的值，避免影响界面上其它绘制；
            // 颜色 / 深度写掩码则保持「全开」，因为界面绘制本来就要求全开，
            // 还原成进入时的错误值只会把污染继续传下去。
            RenderSystem.setShaderColor(backupShaderColor[0], backupShaderColor[1], backupShaderColor[2], backupShaderColor[3]);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);

            if (level.effects().constantAmbientLight()) {
                Lighting.setupNetherLevel(projectionMat);
            } else {
                Lighting.setupLevel(projectionMat);
            }

            matrices.restore(BACKUP_CAMERA_MATRICES);

            matrix4fstack.popPose();
            RenderSystem.applyModelViewMatrix();

            gameRenderer.resetProjectionMatrix(BACKUP_PROJECTION);
            AdvancedFbo.unbind();

            lightTexture.updateLightTexture(minecraft.getFrameTime());
        }
    }

}
