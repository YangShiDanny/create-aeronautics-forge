package dev.ryanhcode.sable.mixinhelpers.loaded_chunk_debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.mixinterface.loaded_chunk_debug.DebugChunkProviderAttachments;
import dev.ryanhcode.sable.mixinterface.loaded_chunk_debug.DebugLevelChunkExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Matrix4f;

@ApiStatus.Internal
public class SableChunkDebugRenderer {

    public static void render(final PoseStack poseStack, final MultiBufferSource bufferSource, final double camX, final double camY, final double camZ) {
        final long time = System.currentTimeMillis();

        final Minecraft minecraft = Minecraft.getInstance();
        final Entity entity = minecraft.gameRenderer.getMainCamera().getEntity();
        final VertexConsumer builder = bufferSource.getBuffer(RenderType.debugLineStrip(1.0F));
        final Matrix4f pose = poseStack.last().pose();

        final ClientLevel level = minecraft.level;
        final int minBuildHeight = level.getMinBuildHeight();
        final int maxBuildHeight = level.getMaxBuildHeight();

        final DebugChunkProviderAttachments attachments = (DebugChunkProviderAttachments) level.getChunkSource();
        for (final LevelChunk chunk : attachments.sable$loadedChunks()) {
            final ChunkPos pos = chunk.getPos();
            final float diff = (float) Mth.clamp(time - ((DebugLevelChunkExtension) chunk).sable$getLastUpdate(), 0.0, 1000.0) / 1000.0F;
            final float red = 1.0F - diff;
            final float blue = 0.0F;

            final float x = (float) (pos.getMinBlockX() - camX);
            final float z = (float) (pos.getMinBlockZ() - camZ);
            float y = (float) (minBuildHeight - camY);
            if (camY > minBuildHeight) {
                y += (10 * ((1 - diff) / 100));
            } else {
                y -= (10 * ((1 - diff) / 100));
            }
            float y1 = (float) (maxBuildHeight - camY);
            if (camY < maxBuildHeight) {
                y1 -= (10 * ((1 - diff) / 100));
            } else {
                y1 += (10 * ((1 - diff) / 100));
            }
            builder.vertex(pose, (float) (x), (float) (y), (float) (z)).color((int) (red * 255), (int) (diff * 255), (int) (blue * 255), 0).endVertex();

            builder.vertex(pose, (float) (x), (float) (y), (float) (z)).color((int) (red * 255), (int) (diff * 255), (int) (blue * 255), 255).endVertex();
            builder.vertex(pose, (float) (x + 16), (float) (y), (float) (z)).color((int) (red * 255), (int) (diff * 255), (int) (blue * 255), 255).endVertex();
            builder.vertex(pose, (float) (x + 16), (float) (y), (float) (z + 16)).color((int) (red * 255), (int) (diff * 255), (int) (blue * 255), 255).endVertex();
            builder.vertex(pose, (float) (x), (float) (y), (float) (z + 16)).color((int) (red * 255), (int) (diff * 255), (int) (blue * 255), 255).endVertex();

            builder.vertex(pose, (float) (x), (float) (y), (float) (z)).color((int) (red * 255), (int) (diff * 255), (int) (blue * 255), 0).endVertex();

            y = y1;
            builder.vertex(pose, (float) (x), (float) (y), (float) (z)).color((int) (red * 255), (int) (diff * 255), (int) (blue * 255), 0).endVertex();

            builder.vertex(pose, (float) (x), (float) (y), (float) (z)).color((int) (red * 255), (int) (diff * 255), (int) (blue * 255), 255).endVertex();
            builder.vertex(pose, (float) (x + 16), (float) (y), (float) (z)).color((int) (red * 255), (int) (diff * 255), (int) (blue * 255), 255).endVertex();
            builder.vertex(pose, (float) (x + 16), (float) (y), (float) (z + 16)).color((int) (red * 255), (int) (diff * 255), (int) (blue * 255), 255).endVertex();
            builder.vertex(pose, (float) (x), (float) (y), (float) (z + 16)).color((int) (red * 255), (int) (diff * 255), (int) (blue * 255), 255).endVertex();

            builder.vertex(pose, (float) (x), (float) (y), (float) (z)).color((int) (red * 255), (int) (diff * 255), (int) (blue * 255), 0).endVertex();
        }

        final ChunkPos ckPos = entity.chunkPosition();
        final float x = (float) (ckPos.x * 16 - camX);
        float y = (float) (minBuildHeight - camY);
        float y1 = (float) (maxBuildHeight - camY);
        final float z = (float) (ckPos.z * 16 - camZ);

        for (int xO = 0; xO < 2; xO++) {
            for (int zO = 0; zO < 2; zO++) {
                builder.vertex(pose, (float) (x + xO * 16), (float) (y), (float) (z + zO * 16)).color(255, 255, 0, 0).endVertex();

                builder.vertex(pose, (float) (x + xO * 16), (float) (y), (float) (z + zO * 16)).color(255, 255, 0, 255).endVertex();
                builder.vertex(pose, (float) (x + xO * 16), (float) (y1), (float) (z + zO * 16)).color(255, 255, 0, 255).endVertex();

                builder.vertex(pose, (float) (x + xO * 16), (float) (y1), (float) (z + zO * 16)).color(255, 255, 0, 0).endVertex();
            }
        }

        y = minBuildHeight;
        y = ((int) (y / 16)) * 16;
        y1 = maxBuildHeight;

        for (int yO = (int) y; yO <= y1 + 1; yO += 16) {
            builder.vertex(pose, (float) (x), (float) ((float) (yO - camY)), (float) (z)).color(0, 0, 255, 0).endVertex();

            builder.vertex(pose, (float) (x), (float) ((float) (yO - camY)), (float) (z)).color(0, 0, 255, 255).endVertex();
            builder.vertex(pose, (float) (x + 16), (float) ((float) (yO - camY)), (float) (z)).color(0, 0, 255, 255).endVertex();
            builder.vertex(pose, (float) (x + 16), (float) ((float) (yO - camY)), (float) (z + 16)).color(0, 0, 255, 255).endVertex();
            builder.vertex(pose, (float) (x), (float) ((float) (yO - camY)), (float) (z + 16)).color(0, 0, 255, 255).endVertex();
            builder.vertex(pose, (float) (x), (float) ((float) (yO - camY)), (float) (z)).color(0, 0, 255, 255).endVertex();

            builder.vertex(pose, (float) (x), (float) ((float) (yO - camY)), (float) (z)).color(0, 0, 255, 0).endVertex();
        }
    }
}
