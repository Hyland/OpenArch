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
package org.nuxeo.rest.auth;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import com.hyland.openarch.ecm.core.api.NuxeoPrincipal;

/**
 * The RolesAllowed annotation is used to specify the roles permitted to access a resource or method. If applied at the
 * class level, the annotation specifies the roles permitted to access all methods in the class. If applied at the
 * method level, the annotation specifies the roles permitted to access the method. If applied at both the class and
 * method level, the method value overrides the class value.
 * <p>
 * The value is an array of role names. The role names must be the String representation of the security roles. The
 * current {@link NuxeoPrincipal} is used to determine if the caller is in the given role.
 *
 * @see RolesAllowedInterceptor
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RUNTIME)
@Documented
public @interface RolesAllowed {
    String[] value() default "";
}
