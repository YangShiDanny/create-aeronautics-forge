package dev.simulated_team.simulated.content.physics_staff;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllSpecialTextures;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.SimulatedClient;
import dev.simulated_team.simulated.index.SimItems;
import dev.simulated_team.simulated.index.SimRenderTypes;
import foundry.veil.api.client.color.Color;
import net.createmod.catnip.outliner.Outliner;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3dc;

import java.util.List;
import java.util.UUID;

public class PhysicsStaffRenderHandler {

    
    private static BlockPos hoverBlockPos = null;

    /**
     * Renders the selection / hovering box for the staff
     */
    public static void renderSelectionBox(final RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        if (Minecraft.getInstance().options.hideGui) {
            return;
        }

        final PoseStack ps = event.getPoseStack();
        ps.pushPose();
        SimulatedClient.PHYSICS_STAFF_CLIENT_HANDLER.onRender(ps);
        ps.popPose();

        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        if (!player.getItemInHand(InteractionHand.MAIN_HAND).is(SimItems.PHYSICS_STAFF.get()) &&
                !player.getItemInHand(InteractionHand.OFF_HAND).is(SimItems.PHYSICS_STAFF.get())) {
            return;
        }

        final Vec3 cameraPos = event.getCamera().getPosition();

        final Level level = player.level();
        final MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        renderAllLocks(bufferSource, ps, level, cameraPos);

        updateHoverPos(minecraft, player);

        if (hoverBlockPos != null) {
            // [1.20.1 移植] veil 的 Color 仅接受 int（ARGB）；原 NeoForge 1.21 的 float 四参构造器不存在
            final Color color = new Color(0xFFBF_BF_BF);

            Outliner.getInstance().showCluster("physicsStaffSelection", List.of(hoverBlockPos))
                    .colored(color.argb())
                    .disableLineNormals()
                    .lineWidth(1 / 32f)
                    .withFaceTexture(AllSpecialTextures.CHECKERED);
        }
    }

    /**
     * Updates the hovered block position
     */
    private static void updateHoverPos(final Minecraft minecraft, final LocalPlayer player) {
        final ClientLevel level = minecraft.level;
        final float partialTicks = minecraft.getFrameTime();

        hoverBlockPos = null;

        final PhysicsStaffClientHandler.ClientDragSession dragSession = SimulatedClient.PHYSICS_STAFF_CLIENT_HANDLER.getDragSession();

        if (dragSession != null) {
            final Vector3dc localAnchor = dragSession.dragLocalAnchor();
            hoverBlockPos = BlockPos.containing(localAnchor.x(), localAnchor.y(), localAnchor.z());
            return;
        }

        final LevelPoseProviderExtension extension = (LevelPoseProviderExtension) level;
        extension.sable$pushPoseSupplier(x -> ((ClientSubLevel) x).renderPose());
        final HitResult hit = player.pick(PhysicsStaffItem.RANGE, partialTicks, false);
        extension.sable$popPoseSupplier();

        if (!(hit instanceof final BlockHitResult blockHitResult) || blockHitResult.getType() == HitResult.Type.MISS) {
            return;
        }

        final Vec3 hitLocation = hit.getLocation();

        final SubLevel subLevel = Sable.HELPER.getContaining(level, hitLocation);
        if (subLevel == null) {
            return;
        }

        hoverBlockPos = blockHitResult.getBlockPos();
    }

    /**
     * Renders all the locks our client is aware about
     */
    private static void renderAllLocks(final MultiBufferSource.BufferSource bufferSource, final PoseStack ps, final Level level, final Vec3 cameraPos) {
        final Minecraft client = Minecraft.getInstance();
        final List<UUID> locks = SimulatedClient.PHYSICS_STAFF_CLIENT_HANDLER.getLocks(level);
        final SubLevelContainer container = SubLevelContainer.getContainer(level);

        for (final UUID lock : locks) {
            final SubLevel subLevel = container.getSubLevel(lock);

            if (!(subLevel instanceof final ClientSubLevel clientSubLevel)) continue;

            ps.pushPose();
            final Vector3dc renderPos = clientSubLevel.renderPose().position();
            ps.translate(renderPos.x() - cameraPos.x(), renderPos.y() - cameraPos.y(), renderPos.z() - cameraPos.z());
            ps.mulPose(client.getEntityRenderDispatcher().cameraOrientation());

            final VertexConsumer buffer = bufferSource.getBuffer(SimRenderTypes.lock());

            final PoseStack.Pose pose = ps.last();
            final int color = 0xffffffff;
            buffer.vertex(pose.pose(), 0.0f - 0.5f, 0.0f - 0.5f, 0.0f).color(color).uv(0.0f, 1.0f).uv2(LightTexture.FULL_BRIGHT).endVertex();
            buffer.vertex(pose.pose(), 0.0f - 0.5f, 1.0f - 0.5f, 0.0f).color(color).uv(0.0f, 0.0f).uv2(LightTexture.FULL_BRIGHT).endVertex();
            buffer.vertex(pose.pose(), 1.0f - 0.5f, 1.0f - 0.5f, 0.0f).color(color).uv(1.0f, 0.0f).uv2(LightTexture.FULL_BRIGHT).endVertex();
            buffer.vertex(pose.pose(), 1.0f - 0.5f, 0.0f - 0.5f, 0.0f).color(color).uv(1.0f, 1.0f).uv2(LightTexture.FULL_BRIGHT).endVertex();

            ps.popPose();
        }
    }

}
