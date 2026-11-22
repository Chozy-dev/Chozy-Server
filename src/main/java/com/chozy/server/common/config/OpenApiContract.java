package com.chozy.server.common.config;

import com.chozy.server.common.error.ErrorCode;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Documents the success message and operation-specific business errors. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OpenApiContract {
  String success();

  ErrorCode[] errors() default {};
}
