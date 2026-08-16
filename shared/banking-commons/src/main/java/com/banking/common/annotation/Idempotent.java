package com.banking.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method as idempotent. The IdempotencyAspect (in each service)
 * intercepts calls, checks the Idempotency-Key header against Redis, and returns
 * the cached response if the key was already processed.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /** TTL in seconds for the cached response. Defaults to 24 hours. */
    long ttlSeconds() default 86400;
}
