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

import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.links.Link;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;

@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@ApiResponse(responseCode = "204")
public @interface ApiResponse204 {
    @AliasFor(annotation = ApiResponse.class, attribute = "description")
    String description() default "";

    @AliasFor(annotation = ApiResponse.class, attribute = "headers")
    Header[] headers() default {};

    @AliasFor(annotation = ApiResponse.class, attribute = "links")
    Link[] links() default {};

    @AliasFor(annotation = ApiResponse.class, attribute = "content")
    Content[] content() default @Content;

    @AliasFor(annotation = ApiResponse.class, attribute = "extensions")
    Extension[] extensions() default {};

    @AliasFor(annotation = ApiResponse.class, attribute = "ref")
    String ref() default "";

    @AliasFor(annotation = ApiResponse.class, attribute = "useReturnTypeSchema")
    boolean useReturnTypeSchema() default false;
}
