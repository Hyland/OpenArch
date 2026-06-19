/*
 * (C) Copyright 2024 Hyland (https://hyland.com/) and others.
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
package org.nuxeo.rest.auth.config;

import static org.nuxeo.ecm.core.api.security.SecurityConstants.ADMINISTRATORS_GROUP;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.POWER_USERS_GROUP;

import org.nuxeo.rest.auth.AdministratorAccess;
import org.nuxeo.rest.auth.RolesAllowed;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class RolesAllowedInterceptorTestConfig {

    @Bean
    public TestBean testBean() {
        return new TestBean();
    }

    @Bean
    public TestBeanWithRolesAllowedPowerUser testBeanWithRolesAllowedPowerUser() {
        return new TestBeanWithRolesAllowedPowerUser();
    }

    @Bean
    public TestBeanWithRolesAllowedAdminAndPowerUser testBeanWithRolesAllowedAdminAndPowerUser() {
        return new TestBeanWithRolesAllowedAdminAndPowerUser();
    }

    @Bean
    public TestBeanWithAdministratorAccess testBeanWithAdministratorAccess() {
        return new TestBeanWithAdministratorAccess();
    }

    public static class TestBean {
        public void testMethod() {
            // do nothing
        }

        @RolesAllowed(ADMINISTRATORS_GROUP)
        public void testMethodWithRolesAllowedAdmin() {
            // do nothing
        }

        @RolesAllowed({ ADMINISTRATORS_GROUP, POWER_USERS_GROUP })
        public void testMethodWithRolesAllowedAdminAndPowerUser() {
            // do nothing
        }

        @AdministratorAccess
        public void testMethodWithAdministratorAccess() {
            // do nothing
        }
    }

    @RolesAllowed(POWER_USERS_GROUP)
    public static class TestBeanWithRolesAllowedPowerUser {
        @AdministratorAccess
        public void testMethodWithRolesAllowedAdmin() {
            // do nothing
        }
    }

    @RolesAllowed({ ADMINISTRATORS_GROUP, POWER_USERS_GROUP })
    public class TestBeanWithRolesAllowedAdminAndPowerUser {
        public void testMethod() {
            // do nothing
        }
    }

    @AdministratorAccess
    public static class TestBeanWithAdministratorAccess {
        public void testMethod() {
            // do nothing
        }
    }

}
