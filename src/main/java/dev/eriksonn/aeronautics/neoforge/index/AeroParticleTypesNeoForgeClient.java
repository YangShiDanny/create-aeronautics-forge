package dev.eriksonn.aeronautics.neoforge.index;

import dev.eriksonn.aeronautics.index.AeroParticleTypes;
import net.minecraft.core.particles.ParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.IEventBus;

// [1.20.1 移植·修复] 仅客户端加载的粒子提供者注册。
// AeroParticleTypes.registerClientParticles 内部经由 lambda 引用客户端类
// RegisterParticleProvidersEvent / ParticleEngine；若放在主类 registerEventListeners 内，
// 即使包在 if(isClient) 里，JVM 校验方法时会解析该裸方法引用，强制加载客户端类，
// 专用服务器 RuntimeDistCleaner 直接抛异常。故独立成客户端类（标 @OnlyIn(CLIENT) 双保险），
// 仅经 DistExecutor 内层 lambda（合成类，服务端永不实例化）进入。
@OnlyIn(Dist.CLIENT)
public final class AeroParticleTypesNeoForgeClient {
    private AeroParticleTypesNeoForgeClient() {}

    public static void register(final IEventBus modEventBus) {
        modEventBus.addListener(AeroParticleTypesNeoForgeClient::registerParticleProviders);
    }

    public static void registerParticleProviders(final RegisterParticleProvidersEvent event) {
        AeroParticleTypes.registerClientParticles((x) -> {
            //noinspection rawtypes,unchecked
            x.getTypeFactory().get().register((ParticleType) x.getObject(), event);
        });
    }
}
