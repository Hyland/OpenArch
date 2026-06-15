/*
 *  (C) Copyright 2026 Hyland (https://www.hyland.com/).
 *
 *  This is unpublished proprietary source code of Hyland. All rights reserved.
 *  Notice of copyright on this source code does not indicate publication.
 */
package org.nuxeo.ecm.platform.login.filestore;

import java.util.List;

import org.nuxeo.ecm.core.api.NuxeoGroup;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.platform.login.filestore.store.UserStore;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.ecm.platform.usermanager.UserManagerDescriptor;
import org.nuxeo.runtime.springboot.framework.ApplicationContextHelper;

/**
 * File-based {@link UserManager} store.
 * <p>
 * Users and groups are defined in a YAML file loaded at application startup (see
 * {@link UserStoreLoader}). The store is held in memory as an immutable snapshot for the lifetime of
 * the application; restarting the service reloads the file.
 * </p>
 * <p>
 * Principal resolution supports two lookup strategies:
 * <ol>
 *   <li>By UUID — used when the caller already has the user's stable identifier (e.g. when
 *       resolving document creators/contributors from stored metadata).</li>
 *   <li>By username (fallback) — required by the HTTP Basic Auth flow, where Spring Security
 *       stores the login name rather than the UUID on the {@code SecurityContext}.</li>
 * </ol>
 * </p>
 * <p>
 * This class is Nuxeo Runtime-managed (registered via {@code OSGI-INF/usermanager-filestore-config.xml})
 * and therefore not a Spring bean. The {@link UserStoreLoader} Spring bean is accessed lazily via
 * {@link ApplicationContextHelper} following the same pattern used by the enterprise
 * {@code UserManagerNucleus}.
 * </p>
 */
public class UserManagerFileStore implements UserManager {

    private static final long serialVersionUID = 1L;

    private transient UserStoreLoader loader;

    private UserStoreLoader getLoader() {
        if (loader == null) {
            loader = ApplicationContextHelper.getApplicationContext().getBean(UserStoreLoader.class);
        }
        return loader;
    }

    @Override
    public NuxeoPrincipal getPrincipal(String userid) {
        if (userid == null || userid.isBlank()) {
            return null;
        }
        UserStore store = getLoader().getStore();
        // Primary lookup by UUID
        NuxeoPrincipal principal = store.findById(userid);
        if (principal != null) {
            return principal;
        }
        // Fallback to username lookup: required for HTTP Basic Auth where the SecurityContext
        // principal name is the login username, not the UUID.
        return store.findByUsername(userid);
    }

    @Override
    public List<NuxeoPrincipal> getPrincipals(List<String> userIds) {
        UserStore store = getLoader().getStore();
        return userIds.stream()
                      .filter(id -> id != null && !id.isBlank())
                      .map(store::findById)
                      .filter(p -> p != null)
                      .toList();
    }

    @Override
    public List<NuxeoPrincipal> searchPrincipalsByName(String name) {
        return getLoader().getStore().searchPrincipalsByName(name);
    }

    @Override
    public NuxeoGroup getGroup(String groupId) {
        return getLoader().getStore().findGroupById(groupId);
    }

    @Override
    public List<NuxeoGroup> getGroups(List<String> groupIds) {
        UserStore store = getLoader().getStore();
        return groupIds.stream()
                       .filter(id -> id != null && !id.isBlank())
                       .map(store::findGroupById)
                       .filter(g -> g != null)
                       .toList();
    }

    @Override
    public List<NuxeoGroup> searchGroupsByName(String groupName) {
        return getLoader().getStore().searchGroupsByName(groupName);
    }

    @Override
    public List<String> getAdministratorsGroups() {
        return getLoader().getStore().getAdministratorsGroups();
    }

    @Override
    public void setConfiguration(UserManagerDescriptor descriptor) {
        // The file-based store does not require dynamic reconfiguration.
    }
}
