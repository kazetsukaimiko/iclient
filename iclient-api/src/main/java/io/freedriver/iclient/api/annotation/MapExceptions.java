package io.freedriver.iclient.api.annotation;

import io.freedriver.iclient.api.exception.IClientExceptionMapper;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Selects a mapper for a client method. A method annotation takes precedence over its
 * declaring interface, then the nearest annotated superinterface in getInterfaces() order.
 * The proxy constructs one instance per mapper class at construction through BeanCreator.
 * Failure to instantiate it raises IClientConstructionException during construction.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface MapExceptions {
    Class<? extends IClientExceptionMapper> using();
}
