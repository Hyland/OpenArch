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
package com.hyland.openarch.ecm.platform.login.filestore;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.io.IOException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;
import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.ecm.platform.login.filestore.config.FileStoreUsersProperties;
import com.hyland.openarch.ecm.platform.login.filestore.store.UserEntry;
import com.hyland.openarch.ecm.platform.login.filestore.store.UserStore;
import com.hyland.openarch.ecm.platform.login.filestore.store.UsersFileModel;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Loads users and groups from the YAML user-store file at application startup and holds an immutable {@link UserStore}
 * snapshot for the lifetime of the application.
 * <p>
 * The file location is controlled by {@link FileStoreUsersProperties#getFile()}. Startup fails fast with a clear error
 * message if the file is absent or cannot be parsed.
 * </p>
 * <p>
 * Also exposes the list of {@link UserDetails} required by Spring Security's {@code InMemoryUserDetailsManager} so that
 * HTTP Basic Auth can validate credentials.
 * </p>
 */
public class UserStoreLoader {

    private static final Logger log = LogManager.getLogger(UserStoreLoader.class);

    private static final String ROLE_USER = "USER";

    private static final String ROLE_ADMIN = "ADMIN";

    private final FileStoreUsersProperties properties;

    private final ResourceLoader resourceLoader;

    private UserStore store;

    private List<UserDetails> userDetails;

    public UserStoreLoader(FileStoreUsersProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() {
        String fileLocation = properties.getFile();
        Resource resource = resourceLoader.getResource(fileLocation);
        if (!resource.exists()) {
            throw new IllegalStateException(
                    "file user-store not found: " + fileLocation + ". Configure 'hxpr.users.file' to point to a valid YAML file.");
        }
        ObjectMapper mapper = YAMLMapper.builder()
                                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                        .build();
        try (var inputStream = resource.getInputStream()) {
            UsersFileModel model = mapper.readValue(inputStream, UsersFileModel.class);
            this.store = UserStore.from(model);
            this.userDetails = buildUserDetails(model, model.getAdministratorsGroups());
            log.info("Loaded {} user(s) and {} group(s) from community user-store: {}", model.getUsers().size(),
                    model.getGroups().size(), fileLocation);
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Failed to parse community user-store file: " + fileLocation, e);
        }
    }

    /**
     * Returns the immutable {@link UserStore} built from the YAML file.
     */
    public UserStore getStore() {
        return store;
    }

    /**
     * Returns the list of {@link UserDetails} to be registered with Spring Security. Only users with a non-blank
     * username and password are included.
     */
    public List<UserDetails> getUserDetails() {
        return userDetails;
    }

    private List<UserDetails> buildUserDetails(UsersFileModel model, List<String> adminGroups) {
        Set<String> adminGroupNames = new HashSet<>(adminGroups);
        model.getUsers()
             .stream()
             .filter(u -> u.getUsername() != null && !u.getUsername().isBlank())
             .filter(u -> u.getPassword() == null || u.getPassword().isBlank())
             .forEach(u -> log.warn(
                     "User '{}' has a username but no password — excluded from HTTP Basic Auth. Set a password in the user-store file.",
                     u.getUsername()));
        return model.getUsers()
                    .stream()
                    .filter(u -> u.getUsername() != null && !u.getUsername().isBlank())
                    .filter(u -> u.getPassword() != null && !u.getPassword().isBlank())
                    .map(u -> toUserDetails(u, adminGroupNames))
                    .toList();
    }

    private UserDetails toUserDetails(UserEntry entry, Set<String> adminGroupNames) {
        boolean isAdmin = entry.getGroups().stream().anyMatch(adminGroupNames::contains);
        String[] roles = isAdmin ? new String[] { ROLE_USER, ROLE_ADMIN } : new String[] { ROLE_USER };
        return User.withUsername(entry.getUsername()).password(entry.getPassword()).roles(roles).build();
    }
}
