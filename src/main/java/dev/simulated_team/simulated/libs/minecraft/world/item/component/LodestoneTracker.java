package dev.simulated_team.simulated.libs.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;

import java.util.Optional;

/**
 * Backport shim for 1.20.5+ {@code LodestoneTracker} — the value type of the
 * {@code minecraft:lodestone_tracker} data component (recovery / lodestone compass).
 */
public final class LodestoneTracker {

    public static final Codec<LodestoneTracker> CODEC = RecordCodecBuilder.create(
            i -> i.group(
                            GlobalPos.CODEC.optionalFieldOf("target").forGetter(LodestoneTracker::target),
                            Codec.BOOL.fieldOf("tracked").forGetter(LodestoneTracker::tracked))
                    .apply(i, LodestoneTracker::new));

    private final Optional<GlobalPos> target;
    private final boolean tracked;

    public LodestoneTracker(final Optional<GlobalPos> target, final boolean tracked) {
        this.target = target;
        this.tracked = tracked;
    }

    public Optional<GlobalPos> target() {
        return target;
    }

    public boolean tracked() {
        return tracked;
    }
}
