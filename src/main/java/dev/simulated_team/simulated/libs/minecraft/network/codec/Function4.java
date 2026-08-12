package dev.simulated_team.simulated.libs.minecraft.network.codec;

/** 4-arity function used by {@link StreamCodec#composite} arity 4. */
@FunctionalInterface
public interface Function4<A, B, C, D, R> {
    R apply(A a, B b, C c, D d);
}
