package dev.eriksonn.aeronautics.plugin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class AeroMixinPlugin implements IMixinConfigPlugin {
    private boolean sodiumPresent;
    private boolean irisPresent;

    @Override
    public void onLoad(final String mixinPackage) {
        // [1.20.1 移植修正] 原版在 onLoad 阶段经 SodiumCompat/IrisCompat 间接调用
        // ModList.get().isLoaded(...)，而 Forge 的 Mixin 配置选择阶段早于 ModList 初始化，
        // 此时 ModList.get() 返回 null，会抛 NullPointerException，导致整个 aeronautics
        // 的 Mixin 配置被跳过（含 balloon.SubLevelAssemblyHelperMixin 等装配钩子），
        // 物理组装器拉杆装配时逻辑缺失 → 同步发包错乱 → 客户端被判"无效数据包"断线。
        // 改为空安全探测：ModList 未就绪即视为未加载（Forge 1.20.1 不带 sodium/iris 这类 Fabric 渲染模组）。
        this.sodiumPresent = isModLoaded("sodium");
        this.irisPresent = isModLoaded("iris");
    }

    private static boolean isModLoaded(final String modId) {
        try {
            final net.minecraftforge.fml.ModList list = net.minecraftforge.fml.ModList.get();
            return list != null && list.isLoaded(modId);
        } catch (final Throwable ignored) {
            return false;
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        if (mixinClassName.startsWith("dev.eriksonn.aeronautics.mixin.render.vanilla")) {
            return !this.sodiumPresent;
        }

        if (mixinClassName.startsWith("dev.eriksonn.aeronautics.mixin.render.sodium")) {
            return this.sodiumPresent;
        }

        if (mixinClassName.startsWith("dev.eriksonn.aeronautics.mixin.render.iris")) {
            return this.irisPresent;
        }

        return true;
    }

    @Override
    public void acceptTargets(final Set<String> myTargets, final Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName, final IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName, final IMixinInfo mixinInfo) {

    }
}
