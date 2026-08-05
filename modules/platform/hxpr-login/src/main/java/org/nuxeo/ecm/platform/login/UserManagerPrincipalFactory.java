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
package org.nuxeo.ecm.platform.login;

import java.security.Principal;

import javax.security.auth.login.LoginException;

import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.platform.usermanager.NuxeoPrincipalImpl;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.api.login.PrincipalFactory;

public class UserManagerPrincipalFactory implements PrincipalFactory {

    @Override
    public Principal createPrincipal(String username) throws LoginException {
        UserManager manager = Framework.getService(UserManager.class);
        if (manager == null) {
            return new NuxeoPrincipalImpl(username);
        }
        NuxeoPrincipal principal = Framework.doPrivileged(() -> manager.getPrincipal(username));
        if (principal != null) {
            return principal;
        }
        throw new LoginException(String.format("principal %s does not exist", username));
    }

}
