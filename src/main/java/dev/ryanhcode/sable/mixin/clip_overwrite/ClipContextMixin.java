package dev.ryanhcode.sable.mixin.clip_overwrite;

import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.ClipContext;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.function.Predicate;

@Mixin(ClipContext.class)
public class ClipContextMixin implements ClipContextExtension {

    @Unique
    
    private SubLevel sable$ignoredSubLevel = null;

    @Unique
    private boolean sable$ignoreMainLevel = false;

    @Unique
    private boolean sable$doNotProject = false;

    @Unique
    
    private Predicate<SubLevel> sable$subLevelIgnoring = null;

    @Override
    public  SubLevel sable$getIgnoredSubLevel() {
        return this.sable$ignoredSubLevel;
    }

    @Override
    public  Predicate<SubLevel> sable$getSubLevelIgnoring() {
        return this.sable$subLevelIgnoring;
    }

    @Override
    public void sable$setIgnoredSubLevel( final SubLevel ignoredSubLevel) {
        this.sable$ignoredSubLevel = ignoredSubLevel;
    }

    @Override
    public void sable$setSubLevelIgnoring( final Predicate<SubLevel> subLevelIgnoring) {
        this.sable$subLevelIgnoring = subLevelIgnoring;
    }

    @Override
    public void sable$setIgnoreMainLevel(final boolean ignoreWorld) {
        this.sable$ignoreMainLevel = ignoreWorld;
    }

    @Override
    public boolean sable$isIgnoreMainLevel() {
        return this.sable$ignoreMainLevel;
    }

    @Override
    public void sable$setDoNotProject(final boolean doNotProject) {
        this.sable$doNotProject = doNotProject;
    }

    @Override
    public boolean sable$doNotProject() {
        return this.sable$doNotProject;
    }
}
