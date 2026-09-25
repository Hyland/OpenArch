/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hyland.openarch.rest.swagger.annotation;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Parameter(description = ParameterDocPath.DESCRIPTION, allowEmptyValue = true, schema = @Schema(pattern = ParameterDocPath.PATH_PATTERN))
@Constraint(validatedBy = {})
@Pattern(regexp = ParameterDocPath.PATH_PATTERN, message = ParameterDocPath.INVALID_DOCUMENT_PATH)
public @interface ParameterDocPath {
    public static final String PATH_PATTERN = "(/)?(.)*";

    public static final String INVALID_DOCUMENT_PATH = "Invalid document path.";

    public static final String NAME = "docPath";

    public static final String DESCRIPTION = "The document path.";

    @AliasFor(annotation = Pattern.class, attribute = "regexp")
    String regexp() default PATH_PATTERN;

    @AliasFor(annotation = Pattern.class, attribute = "message")
    String message() default INVALID_DOCUMENT_PATH;

    @AliasFor(annotation = Pattern.class, attribute = "groups")
    Class<?>[] groups() default {};

    @AliasFor(annotation = Pattern.class, attribute = "payload")
    Class<? extends Payload>[] payload() default {};
}
