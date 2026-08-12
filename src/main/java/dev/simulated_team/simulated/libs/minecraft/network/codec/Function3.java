package dev.simulated_team.simulated.libs.minecraft.network.codec;

/** 3-arity function used by {@link StreamCodec#composite} arity 3. */
@FunctionalInterface
public interface Function3<A, B, C, R> {
    R apply(A a, B b, C c);
}
