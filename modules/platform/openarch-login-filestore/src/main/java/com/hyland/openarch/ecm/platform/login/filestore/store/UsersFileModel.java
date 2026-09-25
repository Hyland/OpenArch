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
package com.hyland.openarch.ecm.platform.login.filestore.store;

import java.util.ArrayList;
import java.util.List;

/**
 * Root model for the YAML user-store file.
 * <p>
 * Example structure:
 * <pre>
 * administratorsGroups:
 *   - administrators
 * groups:
 *   - id: "a1b2c3d4-0000-0000-0000-000000000001"
 *     name: administrators
 * users:
 *   - id: "ff7b0360-3594-416c-b587-2fa4dd0839ce"
 *     username: admin
 *     password: "{bcrypt}$2a$12$..."
 *     firstName: Admin
 *     lastName: User
 *     email: admin@example.com
 *     groups:
 *       - administrators
 *     attributes:
 *       empId: E123
 * </pre>
 */
public class UsersFileModel {

    /**
     * Group names that confer administrator privileges. Referenced by {@link GroupEntry#getName()}.
     */
    private List<String> administratorsGroups = new ArrayList<>();

    private List<GroupEntry> groups = new ArrayList<>();

    private List<UserEntry> users = new ArrayList<>();

    public List<String> getAdministratorsGroups() {
        return administratorsGroups;
    }

    public void setAdministratorsGroups(List<String> administratorsGroups) {
        this.administratorsGroups = administratorsGroups != null ? administratorsGroups : new ArrayList<>();
    }

    public List<GroupEntry> getGroups() {
        return groups;
    }

    public void setGroups(List<GroupEntry> groups) {
        this.groups = groups != null ? groups : new ArrayList<>();
    }

    public List<UserEntry> getUsers() {
        return users;
    }

    public void setUsers(List<UserEntry> users) {
        this.users = users != null ? users : new ArrayList<>();
    }
}
