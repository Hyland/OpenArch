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

import javax.security.auth.login.LoginException;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public interface LoginService {

    String SYSTEM_USERNAME = "system";

    /**
     * Logs in the given principal.
     *
     * @throws LoginException if the principal cannot be created
     */
    LoginContext loginUser(String userId) throws LoginException;

    /**
     * Logs in the given principal (may be system).
     *
     * @throws LoginException if the principal cannot be created
     */
    LoginContext loginUserOrSystem(String userId) throws LoginException;

    /**
     * Logs in a system principal that has all privileges.
     */
    LoginContext loginSystem();

    /**
     * Logs in a system principal that has all privileges.
     *
     * @param originatingId the userid that originated the system principal
     */
    LoginContext loginSystem(String originatingId);

    /**
     * Logs in the given principal.
     *
     * @param principal the principal
     */
    LoginContext loginPrincipal(Principal principal);

}
