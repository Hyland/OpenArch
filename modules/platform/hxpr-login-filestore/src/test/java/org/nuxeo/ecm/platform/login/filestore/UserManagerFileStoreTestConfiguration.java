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
package org.nuxeo.ecm.platform.login.filestore;

import com.hyland.openarch.runtime.springboot.junit.security.MockAuthenticationSupport;
import org.nuxeo.ecm.platform.login.filestore.config.FileStoreUsersProperties;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Spring {@link TestConfiguration} that registers {@link UserManagerFileStore} as the {@code UserManager} bean,
 * backed by the YAML user-store bundled in the test-jar ({@code classpath:hxpr-users.yml}).
 * <p>
 * Use together with {@link UserManagerFileStoreFeature} in tests that exercise the community profile.
 *
 * <pre>{@code
 * @Features({UserManagerCommunityFeature.class, DummyLoginFeature.class})
 * @Import(UserManagerCommunityTestConfiguration.class)
 * @SpringBootTest(classes = { MyController.class })
 * class MyControllerTest extends RuntimeSpringBootTest { ... }
 * }</pre>
 *
 * <p>
 * This configuration also provides a {@link SecurityFilterChain} that requires authentication for every request.
 * Unauthenticated requests receive a {@code 401 Unauthorized} response. Authentication is set up per-test by
 * {@link UserManagerFileStoreFeature} via {@link MockAuthenticationSupport}.
 *
 * @see UserManagerFileStoreFeature
 * @see UserManagerFileStore
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(FileStoreUsersProperties.class)
public class UserManagerFileStoreTestConfiguration {

    @Bean
    public UserStoreLoader userStoreLoader(FileStoreUsersProperties properties) {
        return new UserStoreLoader(properties, new DefaultResourceLoader());
    }

    @Bean
    public UserManager userManagerCommunity() {
        return new UserManagerFileStore();
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                   .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                   .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                   .build();
    }
}
