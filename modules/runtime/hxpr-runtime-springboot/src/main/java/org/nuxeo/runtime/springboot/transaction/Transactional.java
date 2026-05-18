/*
 * (C) Copyright 2021 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     Nelson Silva
 */
package org.nuxeo.runtime.springboot.transaction;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.util.Nonbinding;

/**
 * Interceptor for transaction management done with {@link TransactionalInterceptor}.
 *
 */
@Target({ TYPE, METHOD })
@Retention(RUNTIME)
@Documented
public @interface Transactional {

    /**
     * The transaction timeout in seconds.
     * <p>
     * The default value is 0. If value us &lt;= 0, the transaction service uses the default value.
     */
    @Nonbinding
    int timeout() default -1;

    String UNSET_TIMEOUT_CONFIG_PROPERTY = "<<unset>>";

    /**
     * The configuration property to use in order to determine the value of the timeout in seconds. If the property
     * exists, it must be an integer value representing the transaction timeout in seconds.
     * <p>
     * An example configuration in {@code application.properties} could be: {@code my-transaction.timeout=5}.
     * <p>
     * If both {@code property} and {@code timeout} are set, then Spring will attempt to resolve
     * {@code timeoutFromConfigProperty} and if a value for it has been provided, the timeout is set to that value. If
     * no value has been provided at runtime for the property, then the value of {@code timeout} will be used as the
     * fallback.
     */
    @Nonbinding
    String property() default UNSET_TIMEOUT_CONFIG_PROPERTY;

}
