package dev.ryanhcode.sable.mixin.plot;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.mixin.loaded_chunk_debug.ClientChunkCacheStorageAccessor;
import dev.ryanhcode.sable.mixinterface.loaded_chunk_debug.DebugChunkProviderAttachments;
import dev.ryanhcode.sable.platform.SableChunkEventPlatform;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;

/**
 * Makes the chunk access methods in the client chunk cache use the plot system.
 */
@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCacheMixin implements DebugChunkProviderAttachments {

    @Shadow
    @Final
    private static Logger LOGGER;
    @Shadow
    @Final
    private ClientLevel level;
    @Shadow
    @Final
    private LevelChunk emptyChunk;

    @Shadow
    private static boolean isValidChunk( final LevelChunk levelChunk, final int i, final int j) {
        return false;
    }

    @Shadow
    volatile ClientChunkCache.Storage storage;

    @Unique
    private static long sable$lastChunkLogTime = 0L;

    @Unique
    private  SubLevelContainer sable$getPlotContainer() {
        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);

        if (container == null) {
            throw new IllegalStateException("Plot container not found in level");
        }
        return container;
    }

    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;", at = @At("HEAD"), cancellable = true)
    private void getChunk(final int x, final int z, final ChunkStatus status, final boolean create, final CallbackInfoReturnable<LevelChunk> cir) {
        final SubLevelContainer container = this.sable$getPlotContainer();

        if (container.inBounds(x, z)) {
            final ChunkPos chunkPos = new ChunkPos(x, z);
            final LevelChunk chunk = container.getChunk(chunkPos);

            if (chunk != null) {
                cir.setReturnValue(chunk);
            } else {
                cir.setReturnValue(this.emptyChunk);
            }
        }
    }

    // 1.20.1: ClientChunkCache#drop(int, int) — 1.21 had drop(ChunkPos).
    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void drop(final int chunkX, final int chunkZ, final CallbackInfo ci) {
        final SubLevelContainer container = this.sable$getPlotContainer();
        final ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);

        if (container.inBounds(chunkPos)) {
            ci.cancel();
            throw new UnsupportedOperationException("Cannot drop chunks in plot");
        }
    }

    @Inject(method = "replaceBiomes", at = @At("HEAD"), cancellable = true)
    private void replaceBiomes(final int x, final int z, final FriendlyByteBuf friendlyByteBuf, final CallbackInfo ci) {
        final SubLevelContainer container = this.sable$getPlotContainer();

        if (container.inBounds(x, z)) {
            final ChunkPos chunkPos = new ChunkPos(x, z);
            final LevelChunk levelChunk = container.getChunk(chunkPos);

            if (levelChunk == null || !isValidChunk(levelChunk, x, z)) {
                LOGGER.warn("Ignoring chunk since it's not present: {}, {}", x, z);
            } else {
                levelChunk.replaceBiomes(friendlyByteBuf);
            }
        }
    }

    @Inject(method = "replaceWithPacketData", at = @At("HEAD"), cancellable = true)
    private void replaceWithPacketData(final int x, final int z, final FriendlyByteBuf friendlyByteBuf, final CompoundTag compoundTag,
                                       final Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> consumer, final CallbackInfoReturnable<LevelChunk> cir) {
        final SubLevelContainer container = this.sable$getPlotContainer();

        if (container.inBounds(x, z)) {
            final ChunkPos chunkPos = new ChunkPos(x, z);

            LevelChunk levelChunk = container.getChunk(chunkPos);
            if (!isValidChunk(levelChunk, x, z)) {
                if (levelChunk != null) {
                    SableChunkEventPlatform.INSTANCE.onOldChunkInvalid(levelChunk);
                    this.level.unload(levelChunk);
                }
                levelChunk = new LevelChunk(this.level, chunkPos);
                levelChunk.replaceWithPacketData(friendlyByteBuf, compoundTag, consumer);
                container.newPopulatedChunk(chunkPos, levelChunk);
            } else {
                levelChunk.replaceWithPacketData(friendlyByteBuf, compoundTag, consumer);
            }

            this.level.onChunkLoaded(chunkPos);
            this.level.getLightEngine().setLightEnabled(chunkPos, true);

            SableChunkEventPlatform.INSTANCE.onClientChunkPacketReplaced(levelChunk);

            final LevelPlot plot = container.getPlot(chunkPos);
            // [BUG-29 修复] 整块加载时把整块方块状态播种进子关卡的「上次确认状态」映射，
            // 使周期刷新（velocity_sensor 等机械部件）与空气位都能被正确识别为「已知/无变化」，
            // 避免首次刷新被误判为变更而刷屏，也保证「在空气上放置」能判定为真实变更。
            if (plot != null && plot.getSubLevel() instanceof ClientSubLevel) {
                final ClientSubLevel subLevel = (ClientSubLevel) plot.getSubLevel();
                final LevelChunkSection[] sections = levelChunk.getSections();
                final int baseY = levelChunk.getMinBuildHeight();
                for (int sy = 0; sy < sections.length; sy++) {
                    final LevelChunkSection section = sections[sy];
                    if (section == null || section.hasOnlyAir()) {
                        continue;
                    }
                    final int sectionBaseY = baseY + sy * 16;
                    for (int lx = 0; lx < 16; lx++) {
                        for (int ly = 0; ly < 16; ly++) {
                            for (int lz = 0; lz < 16; lz++) {
                                final BlockState st = section.getBlockState(lx, ly, lz);
                                final BlockPos sable$seedPos = new BlockPos(x * 16 + lx, sectionBaseY + ly, z * 16 + lz);
                                subLevel.sable$setLastKnownState(sable$seedPos, st);
                                // [BUG-30 衍生] 同步播种「最近非空气状态」：空气位不记（setLastNonAirState 内部对空气做移除），
                                // 仅保留真实非空气方块，供破坏反馈在整块加载后首次破坏即能正确取到被破坏的方块。
                                subLevel.sable$setLastNonAirState(sable$seedPos, st);
                            }
                        }
                    }
                }
            }

            cir.setReturnValue(levelChunk);
        }
    }

    @Override
    public Collection<LevelChunk> sable$loadedChunks() {
        final List<LevelChunk> loadedChunks = new LinkedList<>();

        final ClientChunkCacheStorageAccessor accessor = (ClientChunkCacheStorageAccessor) (Object) this.storage;
        if (accessor != null) {
            final AtomicReferenceArray<LevelChunk> chunks = accessor.getChunks();
            for (int i = 0; i < chunks.length(); i++) {
                final LevelChunk chunk = chunks.get(i);
                if (chunk != null) {
                    loadedChunks.add(chunk);
                }
            }
        }

        return loadedChunks;
    }
}
