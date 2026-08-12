package dev.ryanhcode.sable.util;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * A {@link LevelEntityGetter} that delegates all calls to a child, taking into account sub-levels and their plots
 *
 * @param <T>
 */
public class SubLevelInclusiveLevelEntityGetter<T extends EntityAccess> implements LevelEntityGetter<T> {
    public static final int MAX_GET_SIDE_LENGTH = 100_000;

    private final Level level;
    private final LevelEntityGetter<T> delegate;

    public SubLevelInclusiveLevelEntityGetter(final Level level, final LevelEntityGetter<T> delegate) {
        this.level = level;
        this.delegate = delegate;
    }

    /**
     * 已中止的超大 AABB 查询累计次数。渲染线程与主线程都会碰它，用原子计数。
     */
    private static final java.util.concurrent.atomic.AtomicLong ABORTED_COUNT =
            new java.util.concurrent.atomic.AtomicLong();

    /** 带完整堆栈的详细日志最多打印几次。 */
    private static final int STACK_TRACE_LIMIT = 3;

    /** 超过详细次数后，每累计多少次才打一条汇总。 */
    private static final int SUMMARY_INTERVAL = 20_000;

    /**
     * 记录一次「AABB 大到离谱、查询被中止」事件。
     *
     * <p><b>为什么必须节流：</b>这个分支会被其它模组每帧触发若干次
     * （实测 ChangedAddon 的准心检测 PatOverlay → ProjectileUtil.getEntityHitResult
     *  → Level.getEntities 一路打到这里）。原先每次都 {@code new Throwable} 并打完整堆栈，
     * 实测一分钟产出 44 万条、日志文件涨到 1.2 GB。后果有三：
     * <ul>
     *   <li>渲染线程被日志 I/O 阻塞，帧率暴跌 —— 任何画面观察都不再可信；</li>
     *   <li>真正有价值的诊断信息被彻底淹没；</li>
     *   <li>磁盘被写爆。</li>
     * </ul>
     * 因此只有前 {@value #STACK_TRACE_LIMIT} 次打完整堆栈（足够定位调用方），
     * 之后每 {@value #SUMMARY_INTERVAL} 次打一条不含堆栈的汇总。
     *
     * <p><b>注意：</b>这只是止血，不是根治。AABB 之所以横跨 2048 万格，
     * 是因为里面同时混进了宿主世界坐标与子关卡局部坐标
     * （形如 {@code AABB[359.13, -56.38, 34.47] -> [2.0481029E7, 113.47, 2.0481028E7]}），
     * 属于坐标空间转换遗漏，需另行修复。
     */
    private static void logError(final AABB aabb) {
        final long count = ABORTED_COUNT.incrementAndGet();
        if (count <= STACK_TRACE_LIMIT) {
            Sable.LOGGER.error("Aborting entity get for abnormally large AABB: {}（第 {} 次，仅前 {} 次打印堆栈）",
                    aabb, count, STACK_TRACE_LIMIT, new Throwable("Stack Trace"));
        } else if (count % SUMMARY_INTERVAL == 0) {
            Sable.LOGGER.error("Aborting entity get for abnormally large AABB：已累计中止 {} 次（堆栈已省略），最近一次 = {}",
                    count, aabb);
        }
    }

    @Override
    public  T get(final int i) {
        return this.delegate.get(i);
    }

    @Override
    public  T get(final UUID uUID) {
        return this.delegate.get(uUID);
    }

    @Override
    public  Iterable<T> getAll() {
        return this.delegate.getAll();
    }

    @Override
    public <U extends T> void get(final EntityTypeTest<T, U> entityTypeTest, final AbortableIterationConsumer<U> abortableIterationConsumer) {
        this.delegate.get(entityTypeTest, abortableIterationConsumer);
    }

    @Override
    public void get(AABB aABB, final Consumer<T> consumer) {
        if (aABB.getSize() > MAX_GET_SIDE_LENGTH) {
            logError(aABB);
            return;
        }

        final SubLevel subLevel = Sable.HELPER.getContaining(this.level, aABB.getCenter());

        this.delegate.get(aABB, consumer);

        final BoundingBox3d bb = new BoundingBox3d(aABB);
        final Matrix4d bakedMatrix = new Matrix4d();
        if (subLevel != null) {
            aABB = bb.transform(subLevel.logicalPose(), bb).toMojang();

            this.delegate.get(aABB, consumer);
        }

        final Iterable<SubLevel> intersecting = Sable.HELPER.getAllIntersecting(this.level, new BoundingBox3d(bb));

        for (final SubLevel otherSubLevel : intersecting) {
            if (otherSubLevel == subLevel) {
                continue;
            }

            final AABB localBounds = bb.set(aABB).transformInverse(otherSubLevel.logicalPose(), bakedMatrix, bb).toMojang();

            this.delegate.get(localBounds, consumer);
        }
    }

    @Override
    public <U extends T> void get(final  EntityTypeTest<T, U> entityTypeTest, AABB aABB, final AbortableIterationConsumer<U> abortableIterationConsumer) {
        if (aABB.getSize() > MAX_GET_SIDE_LENGTH) {
            logError(aABB);
            return;
        }

        final SubLevel subLevel = Sable.HELPER.getContaining(this.level, aABB.getCenter());
        this.delegate.get(entityTypeTest, aABB, abortableIterationConsumer);

        final BoundingBox3d bb = new BoundingBox3d(aABB);
        if (subLevel != null) {
            aABB = bb.transform(subLevel.logicalPose(), bb).toMojang();

            this.delegate.get(entityTypeTest, aABB, abortableIterationConsumer);
        }

        final Iterable<SubLevel> intersecting = Sable.HELPER.getAllIntersecting(this.level, new BoundingBox3d(bb));

        for (final SubLevel otherSubLevel : intersecting) {
            if (otherSubLevel == subLevel) {
                continue;
            }

            final AABB localBounds = bb.set(aABB).transformInverse(otherSubLevel.logicalPose(), bb).toMojang();

            this.delegate.get(entityTypeTest, localBounds, abortableIterationConsumer);
        }
    }

    public void getIgnoringSubLevels(final AABB aABB, final Consumer<T> consumer) {
        this.delegate.get(aABB, consumer);
    }

    public <U extends T> void getIgnoringSubLevels(final EntityTypeTest<T, U> entityTypeTest, final AABB aABB, final AbortableIterationConsumer<U> abortableIterationConsumer) {
        this.delegate.get(entityTypeTest, aABB, abortableIterationConsumer);
    }
}
