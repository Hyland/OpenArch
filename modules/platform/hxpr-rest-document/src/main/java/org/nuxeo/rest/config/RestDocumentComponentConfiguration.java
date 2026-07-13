/*
 *  (C) Copyright 2026 Hyland (https://www.hyland.com/).
 *
 *  This is unpublished proprietary source code of Hyland. All rights reserved.
 *  Notice of copyright on this source code does not indicate publication.
 */
package org.nuxeo.rest.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Component scanning for the REST document module.
 * <p>
 * This is a plain {@code @Configuration} (not an {@code @AutoConfiguration}) so that it may legitimately carry
 * {@code @ComponentScan}. It is pulled in from
 * {@link com.hyland.hxpr.autoconfiguration.RestDocumentAutoConfiguration} via {@code @Import}. The auto-configuration
 * itself deliberately lives outside {@code org.nuxeo.rest} so it is never picked up by this scan (see the Spring Boot
 * reference, "Locating Auto-configuration Candidates").
 */
@Configuration
@ComponentScan(basePackages = { "org.nuxeo.rest" })
public class RestDocumentComponentConfiguration {
}
