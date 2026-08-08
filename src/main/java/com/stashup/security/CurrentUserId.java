package com.stashup.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the authenticated user's ID into a controller method.
 *
 * <p>Controllers never accept a user ID from the request. Taking it from the security context
 * instead removes the entire class of bug where a caller supplies someone else's identifier.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {}
