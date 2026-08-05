/*
 * (C) Copyright 2019-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.api.local;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.nuxeo.ecm.core.api.local.DummyLoginFeature.TEST_ADMIN_USER;
import static org.nuxeo.ecm.core.api.local.DummyLoginFeature.TEST_ADMIN_USER_NAME;

/**
 * Interface allowing to login as a specific user into Nuxeo Platform.
 * <p>
 * It leverages {@link DummyLoginAs} class and so log in as {@code Administrator} or {@code anonymous} will give
 * specific roles.
 *
 * @since 11.1
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface WithUser {

    /**
     * @return the username to use to login
     */
    String value() default TEST_ADMIN_USER_NAME;

    /**
     * @return the user ID to use to login
     */
    String id() default TEST_ADMIN_USER;

    /**
     * @return optional password override used when retrieving the IDP token for tests
     */
    String password() default "";
}
