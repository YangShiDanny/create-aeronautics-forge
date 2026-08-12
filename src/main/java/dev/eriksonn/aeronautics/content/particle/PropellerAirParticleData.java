package dev.eriksonn.aeronautics.content.particle;

import com.mojang.serialization.Codec;
import com.mojang.brigadier.StringReader;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.foundation.particle.ICustomParticleDataWithSprite;
import dev.eriksonn.aeronautics.index.AeroParticleTypes;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
import dev.simulated_team.simulated.libs.minecraft.network.codec.ByteBufCodecs;
import dev.simulated_team.simulated.libs.minecraft.network.codec.StreamCodec;

public class PropellerAirParticleData implements ParticleOptions, ICustomParticleDataWithSprite<PropellerAirParticleData> {

    private static final Codec<PropellerAirParticleData> CODEC = RecordCodecBuilder.create((i) -> i.group(
                    Codec.BOOL.fieldOf("collision").forGetter((p) -> p.enableCollision),
                    Codec.BOOL.fieldOf("virtual").forGetter(p -> p.isVirtual))
            .apply(i, PropellerAirParticleData::new));

    private static final StreamCodec<FriendlyByteBuf, PropellerAirParticleData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, (p) -> p.enableCollision,
            ByteBufCodecs.BOOL, (p) -> p.isVirtual,
            PropellerAirParticleData::new);


    boolean enableCollision;
    boolean isVirtual;

    public PropellerAirParticleData(boolean enableCollision, boolean isVirtual) {
        this.enableCollision = enableCollision;
        this.isVirtual = isVirtual;
    }

    public PropellerAirParticleData() {
        this(true, false);
    }

    @Override
    public ParticleType<?> getType() {
        return AeroParticleTypes.PROPELLER_AIR_FLOW.get();
    }

    @Override
    public ParticleEngine.SpriteParticleRegistration<PropellerAirParticleData> getMetaFactory() {
        return PropellerAirParticle.Factory::new;
    }

    @Override
    public Codec<PropellerAirParticleData> getCodec(ParticleType<PropellerAirParticleData> particleType) {
        return CODEC;
    }

        public StreamCodec<? super FriendlyByteBuf, PropellerAirParticleData> getStreamCodec() {
        return STREAM_CODEC;
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
    public ParticleOptions.Deserializer<PropellerAirParticleData> getDeserializer() {
        return new ParticleOptions.Deserializer<>() {
            @Override
            public PropellerAirParticleData fromNetwork(final ParticleType<PropellerAirParticleData> type, final FriendlyByteBuf buf) {
                return getStreamCodec().decode(buf);
            }
            @Override
            public PropellerAirParticleData fromCommand(final ParticleType<PropellerAirParticleData> type, final StringReader reader) {
                return new PropellerAirParticleData();
            }
        };
    }

}
