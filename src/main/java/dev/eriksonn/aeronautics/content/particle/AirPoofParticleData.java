package dev.eriksonn.aeronautics.content.particle;

import com.mojang.serialization.Codec;
import com.mojang.brigadier.StringReader;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.particle.ICustomParticleDataWithSprite;
import dev.eriksonn.aeronautics.index.AeroParticleTypes;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import dev.simulated_team.simulated.libs.minecraft.network.codec.StreamCodec;

public class AirPoofParticleData implements ParticleOptions, ICustomParticleDataWithSprite<AirPoofParticleData> {
    private static final Codec<AirPoofParticleData> CODEC = Codec.unit(AirPoofParticleData::new);
    private final StreamCodec<FriendlyByteBuf, AirPoofParticleData> streamCodec = StreamCodec.unit(this);

    @Override
    public ParticleEngine.SpriteParticleRegistration<AirPoofParticleData> getMetaFactory() {
        return AirPoofParticle.Factory::new;
    }

    @Override
    public Codec<AirPoofParticleData> getCodec(final ParticleType<AirPoofParticleData> type) {
        return CODEC;
    }

        public StreamCodec<? super FriendlyByteBuf, AirPoofParticleData> getStreamCodec() {
        return this.streamCodec;
    }

    @Override
    public ParticleType<?> getType() {
        return AeroParticleTypes.AIR_POOF.get();
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
    public ParticleOptions.Deserializer<AirPoofParticleData> getDeserializer() {
        return new ParticleOptions.Deserializer<>() {
            @Override
            public AirPoofParticleData fromNetwork(final ParticleType<AirPoofParticleData> type, final FriendlyByteBuf buf) {
                return getStreamCodec().decode(buf);
            }
            @Override
            public AirPoofParticleData fromCommand(final ParticleType<AirPoofParticleData> type, final StringReader reader) {
                return new AirPoofParticleData();
            }
        };
    }

}
