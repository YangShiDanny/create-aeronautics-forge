package dev.simulated_team.simulated.content.particle;

import com.mojang.serialization.Codec;
import com.mojang.brigadier.StringReader;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.foundation.particle.ICustomParticleDataWithSprite;
import dev.simulated_team.simulated.index.SimParticleTypes;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
import dev.simulated_team.simulated.libs.minecraft.network.codec.ByteBufCodecs;
import dev.simulated_team.simulated.libs.minecraft.network.codec.StreamCodec;

public class MagnetFieldParticleData implements ParticleOptions, ICustomParticleDataWithSprite<MagnetFieldParticleData> {
    public static final Codec<MagnetFieldParticleData> CODEC = RecordCodecBuilder.create((i) -> {
        return i.group(Codec.BOOL.fieldOf("negative").forGetter((p) -> {
            return p.negative;
        })).apply(i, MagnetFieldParticleData::new);
    });
    public static final StreamCodec<ByteBuf, MagnetFieldParticleData> STREAM_CODEC;
    private boolean negative;

    public MagnetFieldParticleData(final boolean negative) {
        this.negative = negative;
    }

    public MagnetFieldParticleData() {
        this.negative = false;
    }

    public ParticleType<?> getType() {
        return SimParticleTypes.MAGNET_FIELD.get();
    }

    public Codec<MagnetFieldParticleData> getCodec(final ParticleType<MagnetFieldParticleData> type) {
        return CODEC;
    }

    public ParticleEngine.SpriteParticleRegistration<MagnetFieldParticleData> getMetaFactory() {
        return MagnetFieldParticle.Factory::new;
    }

    public StreamCodec<? super FriendlyByteBuf, MagnetFieldParticleData> getStreamCodec() {
        return STREAM_CODEC;
    }

    static {
        STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, (p) -> p.negative, MagnetFieldParticleData::new);
    }

    public boolean isNegative() {
        return this.negative;
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
    public ParticleOptions.Deserializer<MagnetFieldParticleData> getDeserializer() {
        return new ParticleOptions.Deserializer<>() {
            @Override
            public MagnetFieldParticleData fromNetwork(final ParticleType<MagnetFieldParticleData> type, final FriendlyByteBuf buf) {
                return getStreamCodec().decode(buf);
            }
            @Override
            public MagnetFieldParticleData fromCommand(final ParticleType<MagnetFieldParticleData> type, final StringReader reader) {
                return new MagnetFieldParticleData();
            }
        };
    }

}

