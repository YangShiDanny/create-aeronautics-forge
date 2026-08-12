package dev.simulated_team.simulated.libs.minecraft.network.codec;

/** 6-arity function used by {@link StreamCodec#composite} arity 6. */
@FunctionalInterface
public interface Function6<A, B, C, D, E, F, R> {
    R apply(A a, B b, C c, D d, E e, F f);
}
