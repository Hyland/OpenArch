/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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

package org.nuxeo.runtime.api.login;

import java.security.Principal;
import java.util.Objects;
import java.util.Optional;

import javax.security.auth.login.LoginException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.runtime.model.DefaultComponent;

public class LoginServiceImpl extends DefaultComponent implements LoginService {

    private static final Logger log = LogManager.getLogger(LoginServiceImpl.class);

    private static final String XP_IMPL = "principalFactory";

    private PrincipalFactory principalFactory;

    @Override
    public void start() {
        Optional<PrincipalFactoryDescriptor> descriptor = getRegistryContribution(XP_IMPL);
        if (!descriptor.isEmpty()) {
            try {
                principalFactory = descriptor.get().klass.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                log.error("Unable to instantiate LoginAs implementation", e);
            }
        }
    }

    @Override
    public LoginContext loginUser(String userId) throws LoginException {
        return loginPrincipal(principalFactory.createPrincipal(userId));
    }

    @Override
    public LoginContext loginUserOrSystem(String userId) throws LoginException {
        return userId == null || SYSTEM_USERNAME.equals(userId) ? loginSystem() : loginUser(userId);
    }

    @Override
    public LoginContext loginSystem() {
        return loginSystem(null);
    }

    @Override
    public LoginContext loginSystem(String userId) {
        return loginPrincipal(new SystemID(userId));
    }

    @Override
    public LoginContext loginPrincipal(Principal principal) {
        return new LoginContext(principal);
    }

    public static class SystemID implements Principal {

        private final String userId;

        public SystemID(String origId) {
            userId = origId == null ? SYSTEM_USERNAME : origId;
        }

        @Override
        public String getName() {
            return userId;
        }

        @Override
        public boolean equals(Object object) {
            if (object instanceof SystemID other) {
                return Objects.equals(userId, other.userId);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return userId == null ? 0 : userId.hashCode();
        }

    }

}
