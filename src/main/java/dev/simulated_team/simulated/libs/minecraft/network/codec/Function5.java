package dev.simulated_team.simulated.libs.minecraft.network.codec;

/** 5-arity function used by {@link StreamCodec#composite} arity 5. */
@FunctionalInterface
public interface Function5<A, B, C, D, E, R> {
    R apply(A a, B b, C c, D d, E e);
}
