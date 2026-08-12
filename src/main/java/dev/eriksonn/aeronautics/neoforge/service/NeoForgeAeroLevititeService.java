package dev.eriksonn.aeronautics.neoforge.service;

import dev.eriksonn.aeronautics.neoforge.index.AeroFluidsNeoForge;
import dev.eriksonn.aeronautics.service.AeroLevititeService;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

public class NeoForgeAeroLevititeService implements AeroLevititeService {

    @Override
    public Item getBucket() {
        final var entry = AeroFluidsNeoForge.LEVITITE_BLEND;
        final boolean bucketPresent = entry.getBucket().isPresent();
        if (!bucketPresent) {
            // 防御：桶没注册上也不要让 Ponder 崩溃整局游戏；返回 null 由调用方兜底（Ponder 标签会跳过）。
            return null;
        }
        return entry.getBucket().orElseThrow();
    }

    @Override
    public Fluid getFluid() {
        return AeroFluidsNeoForge.LEVITITE_BLEND.getSource();
    }
}
