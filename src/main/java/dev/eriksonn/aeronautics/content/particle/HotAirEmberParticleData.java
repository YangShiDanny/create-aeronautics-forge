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

public class HotAirEmberParticleData implements ParticleOptions, ICustomParticleDataWithSprite<HotAirEmberParticleData> {

    private static final Codec<HotAirEmberParticleData> CODEC = RecordCodecBuilder.create((i) -> i.group(
                    Codec.BOOL.fieldOf("isSoul").forGetter((p) -> p.isSoul)
    ).apply(i, HotAirEmberParticleData::new));

    private static final StreamCodec<FriendlyByteBuf, HotAirEmberParticleData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, (p) -> p.isSoul,
            HotAirEmberParticleData::new);

    protected final boolean isSoul;

    public HotAirEmberParticleData(final boolean isSoul) {
        this.isSoul = isSoul;
    }

    public HotAirEmberParticleData() {
        this.isSoul = false;
    }

    @Override
    public ParticleType<?> getType() {
        return AeroParticleTypes.HOT_AIR_EMBER.get();
    }

    @Override
    public ParticleEngine.SpriteParticleRegistration<HotAirEmberParticleData> getMetaFactory() {
        return HotAirEmberParticle.Factory::new;
    }

    @Override
    public Codec<HotAirEmberParticleData> getCodec(final ParticleType<HotAirEmberParticleData> particleType) {
        return CODEC;
    }

        public StreamCodec<? super FriendlyByteBuf, HotAirEmberParticleData> getStreamCodec() {
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
    public ParticleOptions.Deserializer<HotAirEmberParticleData> getDeserializer() {
        return new ParticleOptions.Deserializer<>() {
            @Override
            public HotAirEmberParticleData fromNetwork(final ParticleType<HotAirEmberParticleData> type, final FriendlyByteBuf buf) {
                return getStreamCodec().decode(buf);
            }
            @Override
            public HotAirEmberParticleData fromCommand(final ParticleType<HotAirEmberParticleData> type, final StringReader reader) {
                return new HotAirEmberParticleData();
            }
        };
    }

}
