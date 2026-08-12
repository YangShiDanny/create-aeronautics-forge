package dev.simulated_team.simulated.index.neoforge;

import com.simibubi.create.foundation.utility.CreateLang;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.index.SimParticleTypes;
import net.minecraft.core.particles.ParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class SimParticleTypesImpl {

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Simulated.MOD_ID);

    public static void register(final IEventBus modEventBus) {
        for (final SimParticleTypes type : SimParticleTypes.values()) {
            final String name = CreateLang.asId(type.name());
            PARTICLE_TYPES.register(name, () -> {
                return type.get();
            });
        }

        // [1.20.1 移植·修复] 粒子提供者注册内部经由 SimParticleTypes.registerClientParticles
        // 引用客户端类 RegisterParticleProvidersEvent / ParticleEngine。
        // 不能用 if(isClient) 包裸方法引用——JVM 校验 register 时会解析该引用，
        // 强制加载客户端类，专用服务器 RuntimeDistCleaner 直接抛异常。
        // 改走 DistExecutor 内层 lambda（合成类，仅客户端才被实例化），
        // 服务端永不解析、永不加载客户端类。
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> SimParticleTypesImplClient.register(modEventBus));

        PARTICLE_TYPES.register(modEventBus);
    }
}
