/*
 *  (C) Copyright 2026 Hyland (https://www.hyland.com/).
 *
 *  This is unpublished proprietary source code of Hyland. All rights reserved.
 *  Notice of copyright on this source code does not indicate publication.
 */
package org.nuxeo.ecm.platform.login.filestore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the community user store.
 * <p>
 * The property {@code hxpr.users.file} specifies the location of the YAML file containing users and groups. It accepts
 * any Spring {@code Resource} location, e.g.:
 * <ul>
 *   <li>{@code classpath:hxpr-users.yml} — bundled inside the application JAR (default)</li>
 *   <li>{@code file:/etc/hxpr/users.yml} — external file on the host for production overrides</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "hxpr.users")
public class FileStoreUsersProperties {

    /**
     * Location of the YAML user-store file. Defaults to the bundled classpath resource shipped with the application.
     */
    private String file = "classpath:hxpr-users.yml";

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }
}
