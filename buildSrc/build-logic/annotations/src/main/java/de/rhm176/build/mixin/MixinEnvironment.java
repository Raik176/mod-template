package de.rhm176.build.mixin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface MixinEnvironment {
    String alias() default "default";

    Type type() default Type.COMMON;

    enum Type {
        COMMON,
        CLIENT,
        SERVER
    }
}
