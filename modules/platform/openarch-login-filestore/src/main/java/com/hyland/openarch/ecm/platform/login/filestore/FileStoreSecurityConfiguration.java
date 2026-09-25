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

import static org.springframework.security.config.Customizer.withDefaults;

import com.hyland.openarch.runtime.springboot.security.PublicEndpoints;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for the community profile.
 * <p>
 * Activates HTTP Basic authentication backed by an in-memory user store populated from the YAML
 * user-store file via {@link UserStoreLoader}.
 * </p>
 */
@Configuration
@EnableWebSecurity
public class FileStoreSecurityConfiguration {

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain filterChain(HttpSecurity http, @Autowired(required = false) String[] permitPaths)
            throws Exception {
        http.authorizeHttpRequests(authorize -> {
            authorize.requestMatchers(HttpMethod.GET, PublicEndpoints.ALL.toArray(String[]::new)).permitAll();
            if (permitPaths != null) {
                authorize.requestMatchers(HttpMethod.GET, permitPaths).permitAll();
            }
            authorize.anyRequest().authenticated();
        // CSRF must be explicitly disabled for stateless REST clients (e.g. RestAssured in integration tests).
        // The enterprise profile uses oauth2ResourceServer(), which implicitly applies STATELESS session
        // management and therefore auto-disables CSRF. Basic Auth carries no such implicit policy, so
        // Spring Security's default CSRF filter would block every POST/PUT/DELETE request with a 401/403.
        }).csrf(CsrfConfigurer::disable) // NOSONAR java:S4502
            .cors(withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(basic -> basic.authenticationEntryPoint(new UnauthorizedErrorHandler()));
        return http.build();
    }

    /**
     * Provides the in-memory user store required by HTTP Basic Auth.
     * <p>
     * When Spring Security processes an {@code Authorization: Basic ...} header it must validate the credentials
     * against a {@link UserDetailsService}. Users and their encoded passwords are sourced from the YAML user-store
     * file via {@link UserStoreLoader}.
     * </p>
     */
    @Bean
    public UserDetailsService userDetailsService(UserStoreLoader loader) {
        return new InMemoryUserDetailsManager(loader.getUserDetails());
    }
}
