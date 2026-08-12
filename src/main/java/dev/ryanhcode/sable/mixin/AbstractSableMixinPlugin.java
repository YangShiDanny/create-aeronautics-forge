package dev.ryanhcode.sable.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * No-op Mixin plugin base for the Forge 1.20.1 port. The real plugin logic
 * (which depended on the NeoForge-only Veil rendering API) has been removed; this
 * stub simply accepts every mixin without applying any version/mod constraints.
 */
public abstract class AbstractSableMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(final String mixinPackage) {
        // TEMP diagnostic: force Mixin verbose so a failed injection names the offending injector.
        org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment()
                .setOption(org.spongepowered.asm.mixin.MixinEnvironment.Option.DEBUG_VERBOSE, true);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
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
