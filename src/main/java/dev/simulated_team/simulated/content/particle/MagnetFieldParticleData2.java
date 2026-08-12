package dev.simulated_team.simulated.content.particle;

import com.mojang.serialization.Codec;
import com.mojang.brigadier.StringReader;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.foundation.particle.ICustomParticleDataWithSprite;
import dev.simulated_team.simulated.index.SimParticleTypes;
import io.netty.buffer.ByteBuf;
import dev.simulated_team.simulated.libs.catnip.codecs.stream.CatnipStreamCodecs;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
import dev.simulated_team.simulated.libs.minecraft.network.codec.ByteBufCodecs;
import dev.simulated_team.simulated.libs.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

public class MagnetFieldParticleData2 implements ParticleOptions, ICustomParticleDataWithSprite<MagnetFieldParticleData2> {
    //public static final Codec<MagnetFieldParticleData2> CODEC = RecordCodecBuilder.create((i) -> {
    //    return i.group(Codec.BOOL.fieldOf("negative").forGetter((p) -> {
    //        return p.negative;
    //    })).apply(i, MagnetFieldParticleData2::new);
    //});

    public static final Codec<MagnetFieldParticleData2> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Vec3.CODEC.fieldOf("previous_offset").forGetter(p -> p.previousOffset),
            Vec3.CODEC.fieldOf("next_offset").forGetter(p -> p.nextOffset),
            Codec.BOOL.fieldOf("negative").forGetter(p -> p.negative),
            Codec.INT.fieldOf("timeUntilEnd").forGetter(p -> p.timeUntilEnd)
    ).apply(instance, MagnetFieldParticleData2::new));

    public static final StreamCodec<ByteBuf, MagnetFieldParticleData2> STREAM_CODEC;
    Vec3 previousOffset;
    Vec3 nextOffset;
    private boolean negative;
    private int timeUntilEnd;

    public MagnetFieldParticleData2(final Vec3 previousOffset, final Vec3 nextOffset, final boolean negative, final int timeUntilEnd) {
        this.negative = negative;
        this.previousOffset = previousOffset;
        this.nextOffset = nextOffset;
        this.timeUntilEnd = timeUntilEnd;
    }

    public MagnetFieldParticleData2() {
        this.negative = false;
    }

    public ParticleType<?> getType() {
        return SimParticleTypes.MAGNET_FIELD2.get();
    }

    public Codec<MagnetFieldParticleData2> getCodec(final ParticleType<MagnetFieldParticleData2> type) {
        return CODEC;
    }

    public ParticleEngine.SpriteParticleRegistration<MagnetFieldParticleData2> getMetaFactory() {
        return MagnetFieldParticle2.Factory::new;
    }

    public StreamCodec<? super FriendlyByteBuf, MagnetFieldParticleData2> getStreamCodec() {
        return STREAM_CODEC;
    }

    static {
        STREAM_CODEC = StreamCodec.composite(
                CatnipStreamCodecs.VEC3, p -> p.previousOffset,
                CatnipStreamCodecs.VEC3, p -> p.nextOffset,
                ByteBufCodecs.BOOL, p -> p.negative,
                ByteBufCodecs.INT,p -> p.timeUntilEnd,
                MagnetFieldParticleData2::new);
    }

    public boolean isNegative() {
        return this.negative;
    }

    public int getTimeUntilEnd()
    {
        return this.timeUntilEnd;
    }

    public void setNegative(final boolean negative) {
        this.negative = negative;
    }

    @Override
    public String writeToString() {
        return "";
    }

    @Override
    public void writeToNetwork(final FriendlyByteBuf buf) {
        getStreamCodec().encode(buf, this);
    }

    @Override
    public ParticleOptions.Deserializer<MagnetFieldParticleData2> getDeserializer() {
        return new ParticleOptions.Deserializer<>() {
            @Override
            public MagnetFieldParticleData2 fromNetwork(final ParticleType<MagnetFieldParticleData2> type, final FriendlyByteBuf buf) {
                return getStreamCodec().decode(buf);
            }
            @Override
            public MagnetFieldParticleData2 fromCommand(final ParticleType<MagnetFieldParticleData2> type, final StringReader reader) {
                return new MagnetFieldParticleData2();
            }
        };
    }

}
