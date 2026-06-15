/*
 *  (C) Copyright 2026 Hyland (https://www.hyland.com/).
 *
 *  This is unpublished proprietary source code of Hyland. All rights reserved.
 *  Notice of copyright on this source code does not indicate publication.
 */
package org.nuxeo.ecm.platform.login.filestore.store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a user entry loaded from the YAML user-store file.
 * <p>
 * Passwords must be stored in Spring Security's encoded format, e.g. {@code {bcrypt}$2a$12$...} for production or
 * {@code {noop}password} for testing. Plain-text passwords are never acceptable in production deployments.
 * </p>
 */
public class UserEntry {

    private String id;

    private String username;

    private String password;

    private String firstName;

    private String lastName;

    private String email;

    /**
     * Group names this user belongs to. References the {@link GroupEntry#getName()} field.
     */
    private List<String> groups = new ArrayList<>();

    /**
     * Additional user attributes stored as key-value pairs.
     */
    private Map<String, String> attributes = new HashMap<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public List<String> getGroups() {
        return groups;
    }

    public void setGroups(List<String> groups) {
        this.groups = groups != null ? groups : new ArrayList<>();
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes != null ? attributes : new HashMap<>();
    }
}
