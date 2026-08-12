package org.jspecify.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Backport stub of {@code org.jspecify.annotations.Nullable}. Forge 1.20.1 does not bundle
 * jspecify; the merged aeronautics build only references it as a compile-time nullness hint,
 * so this no-op annotation lets those imports resolve. It has no runtime effect.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE})
public @interface Nullable {
}
