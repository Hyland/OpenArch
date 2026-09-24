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

import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.ADMINISTRATORS_GROUP;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.hyland.openarch.ecm.core.api.security.SecurityConstants;

/**
 * Annotation to be used on methods or classes to restrict access to administrators.
 * <p>
 * This annotation is equivalent to the {@link RolesAllowed} annotation with the value
 * {@link SecurityConstants#ADMINISTRATORS_GROUP}.
 * <p>
 * Example of use:
 *
 * <pre>
 * {@code
 * @AdministratorAccess
 * public void myMethod() {
 *     // only administrators can call this method
 * }
 * }
 * </pre>
 *
 * @since 5.7
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@RolesAllowed(ADMINISTRATORS_GROUP)
public @interface AdministratorAccess {

}
