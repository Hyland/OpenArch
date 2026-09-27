/*
 * (C) Copyright 2014-2026 Hyland (https://www.hyland.com/).
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

package com.hyland.openarch.ecm.platform.usermanager;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.hyland.openarch.ecm.core.api.RepositoryGroup;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.core.api.SystemPrincipal;
import com.hyland.openarch.ecm.core.api.security.SecurityConstants;
import com.hyland.openarch.ecm.core.schema.types.resolver.AbstractObjectResolver;
import com.hyland.openarch.ecm.core.schema.types.resolver.ObjectResolver;
import com.hyland.openarch.runtime.api.Framework;

/**
 * This {@link ObjectResolver} allows to manage integrity for fields containing group or user references.
 * <p>
 * References should have a prefix. RepositoryPrincipal.PREFIX for users, RepositoryGroup.PREFIX for groups.
 * </p>
 * <p>
 * If only user or group are configured, the prefix is not needed but still supported. If noth user and group are
 * configured, reference without prefix are resolved as user first.
 * </p>
 * <p>
 * To use it, put the following code in your schema XSD :
 * </p>
 *
 * <pre>
 * {@code
 * <!-- user or group resolver -->
 * <xs:simpleType name="userOrGroupReference">
 *   <xs:restriction base="xs:string" ref:resolver="userManagerResolver" />
 * </xs:simpleType>
 *
 * <!-- user resolver -->
 * <xs:simpleType name="userReference">
 *   <xs:restriction base="xs:string" ref:resolver="userManagerResolver" ref:type="user" />
 * </xs:simpleType>
 *
 * <!-- group resolver -->
 * <xs:simpleType name="groupReference">
 *   <xs:restriction base="xs:string" ref:resolver="userManagerResolver" ref:type="group" />
 * </xs:simpleType>
 * }
 * </pre>
 *
 * @since 7.1
 */
public class UserManagerResolver extends AbstractObjectResolver implements ObjectResolver {

    private static final long serialVersionUID = 1L;

    public static final String INPUT_PARAM_FILTER = "type";

    public static final String FILTER_GROUP = "group";

    public static final String FILTER_USER = "user";

    protected static final String MESSAGES_BUNDLE = "user_messages";

    private boolean includingUsers = true;

    private boolean includingGroups = true;

    private transient UserManager userManager;

    public UserManager getUserManager() {
        if (userManager == null) {
            userManager = Framework.getService(UserManager.class);
        }
        return userManager;
    }

    private List<Class<?>> managedClasses = null;

    @Override
    public List<Class<?>> getManagedClasses() {
        if (managedClasses == null) {
            managedClasses = new ArrayList<>();
            if (includingUsers) {
                managedClasses.add(RepositoryPrincipal.class);
            }
            if (includingGroups) {
                managedClasses.add(RepositoryGroup.class);
            }
        }
        return managedClasses;
    }

    @Override
    public void configure(Map<String, String> parameters) throws IllegalStateException {
        super.configure(parameters);
        String filter = parameters.get(INPUT_PARAM_FILTER);
        if (FILTER_USER.equals(filter)) {
            includingGroups = false;
            this.parameters.put(INPUT_PARAM_FILTER, filter);
        } else if (FILTER_GROUP.equals(filter)) {
            includingUsers = false;
            this.parameters.put(INPUT_PARAM_FILTER, filter);
        }
    }

    @Override
    public Object fetch(Object value) throws IllegalStateException {
        checkConfig();
        if (value instanceof String name) {
            boolean userPrefix = name.startsWith(RepositoryPrincipal.PREFIX);
            boolean groupPrefix = name.startsWith(RepositoryGroup.PREFIX);
            if (includingUsers && !includingGroups) {
                if (userPrefix) {
                    name = name.substring(RepositoryPrincipal.PREFIX.length());
                }
                if (SecurityConstants.SYSTEM_USERNAME.equals(name)) {
                    return new SystemPrincipal();
                }
                return getUserManager().getPrincipal(name);
            } else if (!includingUsers && includingGroups) {
                if (groupPrefix) {
                    name = name.substring(RepositoryGroup.PREFIX.length());
                }
                return getUserManager().getGroup(name);
            } else {
                if (userPrefix) {
                    name = name.substring(RepositoryPrincipal.PREFIX.length());
                    if (SecurityConstants.SYSTEM_USERNAME.equals(name)) {
                        return new SystemPrincipal();
                    }
                    return getUserManager().getPrincipal(name);
                } else if (groupPrefix) {
                    name = name.substring(RepositoryGroup.PREFIX.length());
                    return getUserManager().getGroup(name);
                } else {
                    if (SecurityConstants.SYSTEM_USERNAME.equals(name)) {
                        return new SystemPrincipal();
                    }
                    RepositoryPrincipal principal = getUserManager().getPrincipal(name);
                    if (principal != null) {
                        return principal;
                    } else {
                        return getUserManager().getGroup(name);
                    }
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T fetch(Class<T> type, Object value) throws IllegalStateException {
        checkConfig();
        Object principal = fetch(value);
        if (type.isInstance(principal)) {
            return (T) principal;
        }
        return null;
    }

    @Override
    public Serializable getReference(Object entity) throws IllegalStateException {
        checkConfig();
        if (entity != null) {
            if (entity instanceof RepositoryPrincipal repositoryPrincipal && includingUsers) {
                return RepositoryPrincipal.PREFIX + repositoryPrincipal.getId();
            } else if (entity instanceof RepositoryGroup repositoryGroup && includingGroups) {
                return RepositoryGroup.PREFIX + repositoryGroup.getId();
            }
        }
        return null;
    }

    @Override
    public String getConstraintErrorMessage(Object invalidValue, Locale locale) throws IllegalStateException {
        checkConfig();
        if (isIncludingUsers() && isIncludingGroups()) {
            return getConstraintErrorMessage(MESSAGES_BUNDLE, "any", invalidValue, locale);
        } else if (!isIncludingUsers() && isIncludingGroups()) {
            return getConstraintErrorMessage(MESSAGES_BUNDLE, "group", invalidValue, locale);
        } else if (isIncludingUsers() && !isIncludingGroups()) {
            return getConstraintErrorMessage(MESSAGES_BUNDLE, "user", invalidValue, locale);
        }
        return getDefaultConstrainErrorMessage(invalidValue);
    }

    public boolean isIncludingUsers() throws IllegalStateException {
        checkConfig();
        return includingUsers;
    }

    public boolean isIncludingGroups() throws IllegalStateException {
        checkConfig();
        return includingGroups;
    }

}
