package dev.ryanhcode.sable.mixin.plot;

import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Either;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.*;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Makes the chunk access methods in server chunk caches use the plot system.
 */
@Mixin(ServerChunkCache.class)
public class ServerChunkCacheMixin {

    @Shadow
    @Final
    ServerLevel level;

    @Unique
    private EmptyLevelChunk sable$emptyChunk;

    @Unique
    private static long sable$lastEmptyLog = 0L;

    @Unique
    private static boolean sable$canLogEmpty() {
        final long now = System.currentTimeMillis();
        if (now - sable$lastEmptyLog > 2000L) {
            sable$lastEmptyLog = now;
            return true;
        }
        return false;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    public void init(final ServerLevel serverLevel, final LevelStorageSource.LevelStorageAccess levelStorageAccess, final DataFixer dataFixer, final StructureTemplateManager structureTemplateManager,
                     final Executor executor, final ChunkGenerator chunkGenerator, final int i, final int j, final boolean bl, final ChunkProgressListener chunkProgressListener,
                     final ChunkStatusUpdateListener chunkStatusUpdateListener, final Supplier supplier, final CallbackInfo ci) {
        this.sable$emptyChunk = new EmptyLevelChunk(serverLevel, new ChunkPos(0, 0), serverLevel.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS));
    }

    @Unique
    private  SubLevelContainer sable$getPlotContainer() {
        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);

        if (container == null) {
            throw new IllegalStateException("Plot container not found in level");
        }
        return container;
    }

    @Inject(method = "getChunkNow", at = @At("HEAD"), cancellable = true)
    private void getChunkNow(final int x, final int z, final CallbackInfoReturnable<LevelChunk> cir) {
        final SubLevelContainer container = this.sable$getPlotContainer();
        if (container.inBounds(x, z)) {
            final LevelChunk chunk = container.getChunk(new ChunkPos(x, z));

            cir.setReturnValue(chunk);
        }
    }

    @Inject(method = "getChunkFuture(IILnet/minecraft/world/level/chunk/ChunkStatus;Z)Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"), cancellable = true)
    private void getChunkFuture(final int x, final int z, final ChunkStatus chunkStatus, final boolean bl, final CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> cir) {
        final SubLevelContainer container = this.sable$getPlotContainer();

        if (container.inBounds(x, z)) {
            final ChunkPos chunkPos = new ChunkPos(x, z);
            final LevelChunk chunk = container.getChunk(chunkPos);

            if (chunk != null) {
                cir.setReturnValue(CompletableFuture.completedFuture(Either.left(chunk)));
            } else {
                cir.setReturnValue(CompletableFuture.completedFuture(Either.left(this.sable$emptyChunk)));
            }
        }
    }

    // [1.20.1 移植修正] 12:45 服务端崩溃根因：1.20.1 下 Level.getFluidState/getBlockState -> getChunk(IILChunkStatus;Z)
    // 走私有 getChunkFutureMainThread(m_8456_) 的原版 ticket/holder 加载链；对地块（plot）坐标（约 2048 万格外）
    // 该链找不到 chunk holder -> IllegalStateException("No chunk holder after ticket has been added") -> Ticking player 崩溃。
    // 原 mixin 只拦了 getChunkNow/getChunkFuture（公开重载），漏了这条最常用路径。
    // 语义与上面 getChunkFuture 拦截完全一致：地块范围内直接返回地块区块，没有则返回空区块，绝不进原版加载链。
    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;", at = @At("HEAD"), cancellable = true)
    private void sable$plotGetChunk(final int x, final int z, final ChunkStatus chunkStatus, final boolean load, final CallbackInfoReturnable<ChunkAccess> cir) {
        final SubLevelContainer container = this.sable$getPlotContainer();
        if (container.inBounds(x, z)) {
            final LevelChunk chunk = container.getChunk(new ChunkPos(x, z));
            if (chunk == null && sable$canLogEmpty()) {
            }
            cir.setReturnValue(chunk != null ? chunk : this.sable$emptyChunk);
        }
    }

    @Inject(method = "hasChunk", at = @At("HEAD"), cancellable = true)
    private void hasChunk(final int x, final int z, final CallbackInfoReturnable<Boolean> cir) {
        final SubLevelContainer container = this.sable$getPlotContainer();
        if (container.inBounds(x, z)) {
            final ChunkAccess chunk = container.getChunk(new ChunkPos(x, z));

            cir.setReturnValue(chunk != null);
        }
    }


    @Inject(method = "getChunkForLighting", at = @At("HEAD"), cancellable = true)
    private void getChunkForLighting(final int x, final int z, final CallbackInfoReturnable<LightChunk> cir) {
        final SubLevelContainer container = this.sable$getPlotContainer();
        if (container.inBounds(x, z)) {
            final LevelChunk chunk = container.getChunk(new ChunkPos(x, z));

            cir.setReturnValue(chunk);
        }
    }

    @Inject(method = "isPositionTicking", at = @At("HEAD"), cancellable = true)
    private void isPositionTicking(final long pos, final CallbackInfoReturnable<Boolean> cir) {
        final SubLevelContainer container = this.sable$getPlotContainer();
        if (container.inBounds(ChunkPos.getX(pos), ChunkPos.getZ(pos))) {
            final ChunkPos chunkPos = new ChunkPos(pos);
            final LevelChunk chunk = container.getChunk(chunkPos);

            cir.setReturnValue(chunk != null);
        }
    }

    @Inject(method = "getFullChunk", at = @At("HEAD"), cancellable = true)
    private void getFullChunk(final long pos, final Consumer<LevelChunk> consumer, final CallbackInfo ci) {
        final SubLevelContainer container = this.sable$getPlotContainer();
        if (container.inBounds(ChunkPos.getX(pos), ChunkPos.getZ(pos))) {
            final ChunkPos chunkPos = new ChunkPos(pos);
            final LevelChunk chunk = container.getChunk(chunkPos);

            if (chunk != null) {
                consumer.accept(chunk);
            }

            ci.cancel();
        }
    }

    @Inject(method = "blockChanged", at = @At("HEAD"), cancellable = true)
    private void blockChanged(final BlockPos blockPos, final CallbackInfo ci) {
        final SubLevelContainer container = this.sable$getPlotContainer();

        final ChunkPos pos = new ChunkPos(blockPos);
        if (container.inBounds(pos)) {
            final PlotChunkHolder holder = container.getChunkHolder(pos);

            if (holder == null) {
                throw new UnsupportedOperationException("Cannot change blocks in nonexistent plot holder");
            }

            holder.blockChanged(blockPos);
            ci.cancel();
        }
    }

    @Inject(method = "getVisibleChunkIfPresent", at = @At("HEAD"), cancellable = true)
    private void getVisibleChunkIfPresent(final long l, final CallbackInfoReturnable<ChunkHolder> cir) {
        final int x = ChunkPos.getX(l);
        final int z = ChunkPos.getZ(l);

        final SubLevelContainer container = this.sable$getPlotContainer();
        if (container.inBounds(x, z)) {
            final ChunkPos chunkPos = new ChunkPos(x, z);
            final PlotChunkHolder holder = container.getChunkHolder(chunkPos);

            cir.setReturnValue(holder);
        }
    }

    // [1.20.1 port] 还原 NeoForge 1.21 原始签名 addRegionTicket(TicketType, ChunkPos, int, T)（4 参数，无 forceTicks）。
    // 1.20.1 的 ServerChunkCache 同时存在 4 参数与 5 参数（含 boolean forceTicks）两个重载，
    // 必须用精确描述符锁定 4 参数版本，否则 Mixin 因重载歧义 + handler 参数数不匹配而抛 InvalidInjectionException。
    @Inject(method = "addRegionTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;)V", at = @At("HEAD"), cancellable = true)
    private <T> void addRegionTicket(final TicketType<T> type, final ChunkPos pos, final int distance, final T value, final CallbackInfo ci) {
        final SubLevelContainer container = this.sable$getPlotContainer();
        if (container.inBounds(pos)) {
            ci.cancel();
        }
    }
}
