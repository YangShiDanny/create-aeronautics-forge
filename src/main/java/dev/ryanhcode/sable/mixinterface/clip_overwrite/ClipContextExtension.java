package dev.ryanhcode.sable.mixinterface.clip_overwrite;

import dev.ryanhcode.sable.sublevel.SubLevel;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public interface ClipContextExtension {
     SubLevel sable$getIgnoredSubLevel();

     Predicate<SubLevel> sable$getSubLevelIgnoring();

    void sable$setIgnoredSubLevel( SubLevel sable$ignoredSubLevel);

    void sable$setSubLevelIgnoring( Predicate<SubLevel> sable$subLevelIgnoring);

    void sable$setIgnoreMainLevel(boolean ignoreWorld);

    boolean sable$isIgnoreMainLevel();

    void sable$setDoNotProject(boolean doNotProject);

    boolean sable$doNotProject();
}
