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

package org.nuxeo.ecm.platform.usermanager;

import com.hyland.openarch.ecm.core.api.NuxeoException;
import com.hyland.openarch.ecm.core.api.security.AdministratorGroupsProvider;
import com.hyland.openarch.runtime.model.DefaultComponent;

public class UserService extends DefaultComponent {

    protected static final String XP = "userManager";

    private UserManager userManager;

    @Override
    public void start() {
        UserManagerDescriptor merged = this.<UserManagerDescriptor> getRegistryContribution(
                XP).orElseThrow(() -> new NuxeoException("No contributions registered for the userManager"));

        Class<?> klass = merged.userManagerClass;
        if (klass == null) {
            throw new NuxeoException("No class specified for the userManager");
        }
        try {
            userManager = (UserManager) klass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new NuxeoException(e);
        }
        userManager.setConfiguration(merged);
    }

    @Override
    public void stop() throws InterruptedException {
        userManager = null;
    }

    public UserManager getUserManager() {
        return userManager;
    }

    @Override
    public <T> T getAdapter(Class<T> adapter) {
        if (UserManager.class == adapter || AdministratorGroupsProvider.class == adapter) {
            return adapter.cast(getUserManager());
        }
        return null;
    }
}
