package dev.simulated_team.simulated.libs;

import io.netty.buffer.ByteBuf;
import dev.simulated_team.simulated.libs.minecraft.network.codec.StreamCodec;

import java.util.Objects;

/**
 * Backport shim of Create's {@code net.createmod.catnip.data.Pair}.
 *
 * <p>原本放在 {@code net.createmod.catnip.data} 命名空间，但运行时 Ponder 模块（Create 的 catnip 库）
 * 也拥有并导出该包给本 mod，触发 Java 模块系统的"分包冲突"（split package）导致游戏启动即崩溃。
 * 因此本类迁移到本 mod 自有的 {@code dev.simulated_team.simulated.libs} 包，消除冲突，
 * 同时保留原 NeoForge 1.21 代码用到的全部辅助方法（{@link #first}/{@link #second}/
 * {@link #getLeft}/{@link #getRight}/{@link #streamCodec}）。
 */
public final class Pair<A, B> {

    private final A first;
    private final B second;

    public Pair(final A first, final B second) {
        this.first = first;
        this.second = second;
    }

    public static <A, B> Pair<A, B> of(final A first, final B second) {
        return new Pair<>(first, second);
    }

    public A getFirst() {
        return this.first;
    }

    public B getSecond() {
        return this.second;
    }

    public A first() {
        return this.first;
    }

    public B second() {
        return this.second;
    }

    public A getLeft() {
        return this.first;
    }

    public B getRight() {
        return this.second;
    }

    public Pair<B, A> swap() {
        return new Pair<>(this.second, this.first);
    }

    public static <A, B> StreamCodec<ByteBuf, Pair<A, B>> streamCodec(
            final StreamCodec<ByteBuf, A> firstCodec,
            final StreamCodec<ByteBuf, B> secondCodec) {
        return StreamCodec.composite(
                firstCodec, Pair::getFirst,
                secondCodec, Pair::getSecond,
                Pair::new);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof final Pair<?, ?> pair)) return false;
        return Objects.equals(this.first, pair.first) && Objects.equals(this.second, pair.second);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.first, this.second);
    }

    @Override
    public String toString() {
        return "Pair[" + this.first + ", " + this.second + "]";
    }
}
