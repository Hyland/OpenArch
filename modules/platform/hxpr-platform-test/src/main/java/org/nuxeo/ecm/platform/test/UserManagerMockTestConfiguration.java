/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
 *
 * This is unpublished proprietary source code of Hyland. All rights reserved.
 * Notice of copyright on this source code does not indicate publication.
 */
package org.nuxeo.ecm.platform.test;

import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Spring {@link TestConfiguration} that registers {@link UserManagerMock} as the {@code UserManager} bean.
 * <p>
 * Use together with {@link UserManagerMockFeature} in community/platform tests that need a {@code UserManager}
 * without a dependency on {@code hxpr-login-hxp}.
 *
 * <pre>{@code
 * @Features({UserManagerMockFeature.class, DummyLoginFeature.class})
 * @Import(UserManagerMockTestConfiguration.class)
 * @SpringBootTest(classes = { MyController.class })
 * class MyControllerTest extends RuntimeSpringBootTest { ... }
 * }</pre>
 *
 * <p>
 * This configuration also provides a {@link SecurityFilterChain} that requires authentication for every request.
 * Unauthenticated requests receive a {@code 401 Unauthorized} response. Authentication is set up per-test by
 * {@link UserManagerMockFeature} via {@link MockAuthenticationSupport}.
 */
@TestConfiguration
@EnableWebSecurity
public class UserManagerMockTestConfiguration {

    @Bean
    public UserManager userManagerMock() {
        return new UserManagerMock();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                   .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                   .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                   .build();
    }
}
