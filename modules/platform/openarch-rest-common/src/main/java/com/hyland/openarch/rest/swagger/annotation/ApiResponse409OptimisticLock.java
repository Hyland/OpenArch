/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The optimistic lock failure of a v2 configuration write, described the same way by every such API. */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@ApiResponse409(description = ApiResponse409OptimisticLock.OPTIMISTIC_LOCK_FAILURE)
public @interface ApiResponse409OptimisticLock {
    public static final String OPTIMISTIC_LOCK_FAILURE = "Optimistic lock failure: the version in the request no longer matches the stored version. Re-read the configuration and retry, ideally after an exponential backoff with jitter.";
}
