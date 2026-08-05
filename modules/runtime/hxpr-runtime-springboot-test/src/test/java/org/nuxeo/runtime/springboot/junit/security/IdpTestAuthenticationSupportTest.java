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
package org.nuxeo.runtime.springboot.junit.security;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.local.WithUser;
import org.nuxeo.runtime.api.Framework;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;

class IdpTestAuthenticationSupportTest {

    private static final int HTTP_OK = 200;
    private static final long TOKEN_EXPIRY_SECONDS = 3600;

    private MockedStatic<Framework> frameworkMock;
    private HttpClient mockHttpClient;
    private HttpResponse<InputStream> mockResponse;

    @BeforeEach
    void setUp() {
        frameworkMock = mockStatic(Framework.class);
        IdpTestAuthenticationSupport.clear();

        // Inject mock HttpClient using the new testing support method
        mockHttpClient = mock(HttpClient.class);
        IdpTestAuthenticationSupport.setHttpClientForTesting(mockHttpClient);
    }

    @AfterEach
    void tearDown() {
        if (frameworkMock != null) {
            frameworkMock.close();
        }
        // clear() also resets the HttpClient override
        IdpTestAuthenticationSupport.clear();
    }

    @Test
    void testAuthenticateSuccess() throws Exception {
        // Given
        String username = "testuser";
        String password = "testpass";
        String tokenValue = createValidJwtToken();

        setupDefaultFrameworkProperties();
        setupMockHttpClient(HTTP_OK, createTokenResponse(tokenValue));

        WithUser withUser = mockWithUser(username, password);

        // When
        JwtAuthenticationToken result = IdpTestAuthenticationSupport.authenticate(withUser);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getToken().getTokenValue()).isEqualTo(tokenValue);
        assertThat(result.getName()).isNotBlank();
        assertThat(IdpTestAuthenticationSupport.currentAuthentication()).isEqualTo(result);
        assertThat(IdpTestAuthenticationSupport.bearerToken()).isEqualTo(tokenValue);
    }

    @Test
    void testAuthenticateWithNullWithUser() throws Exception {
        // Given
        String tokenValue = createValidJwtToken();

        setupDefaultFrameworkProperties();
        setupMockHttpClient(HTTP_OK, createTokenResponse(tokenValue));

        // When
        JwtAuthenticationToken result = IdpTestAuthenticationSupport.authenticate(null);

        // Then
        assertThat(result).isNotNull();
        assertThat(IdpTestAuthenticationSupport.bearerToken()).isEqualTo(tokenValue);
    }

    @Test
    void testAuthenticateWithBlankUsername() throws Exception {
        // Given
        String tokenValue = createValidJwtToken();

        setupDefaultFrameworkProperties();
        setupMockHttpClient(HTTP_OK, createTokenResponse(tokenValue));

        WithUser withUser = mockWithUser("", "password");

        // When
        JwtAuthenticationToken result = IdpTestAuthenticationSupport.authenticate(withUser);

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    void testAuthenticateWithCustomProperties() throws Exception {
        // Given
        String customUsername = "customuser";
        String customPassword = "custompass";
        String customTokenUrl = "http://custom-idp.test/token";
        String customClientId = "custom-client";
        String customClientSecret = "custom-secret";
        String customScope = "custom-scope";
        String tokenValue = createValidJwtToken();

        setupFrameworkProperties(customUsername, customPassword, customTokenUrl, customClientId,
                customClientSecret, customScope);
        setupMockHttpClient(HTTP_OK, createTokenResponse(tokenValue));

        // When
        JwtAuthenticationToken result = IdpTestAuthenticationSupport.authenticate(null);

        // Then
        assertThat(result).isNotNull();
    }

    @ParameterizedTest
    @CsvSource({
        "401, '{}', 'IDP token endpoint returned status 401'",
        "200, '{}', 'IDP response does not contain an access_token'",
        "200, '{\"access_token\":\"\"}', 'IDP response does not contain an access_token'"
    })
    void testAuthenticateWithInvalidResponse(int statusCode, String responseBody, String expectedErrorMessage) throws Exception {
        // Given
        setupDefaultFrameworkProperties();
        setupMockHttpClient(statusCode, responseBody);

        WithUser withUser = mockWithUser("user", "pass");

        // When / Then
        assertThatThrownBy(() -> IdpTestAuthenticationSupport.authenticate(withUser))
                .isInstanceOf(NuxeoException.class)
                .hasMessageContaining(expectedErrorMessage);
    }

    @Test
    void testAuthenticateInterrupted() throws Exception {
        // Given
        setupDefaultFrameworkProperties();

        // Configure the injected mock to throw InterruptedException
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("Test interruption"));

        WithUser withUser = mockWithUser("user", "pass");

        // When / Then
        assertThatThrownBy(() -> IdpTestAuthenticationSupport.authenticate(withUser)).isInstanceOf(
                NuxeoException.class).hasMessageContaining("Interrupted while fetching IDP token");

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted(); // Clear flag
    }

    @Test
    void testAuthenticateIOException() throws Exception {
        // Given
        setupDefaultFrameworkProperties();

        // Configure the injected mock to throw IOException
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Network error"));

        WithUser withUser = mockWithUser("user", "pass");

        // When / Then
        assertThatThrownBy(() -> IdpTestAuthenticationSupport.authenticate(withUser)).isInstanceOf(
                NuxeoException.class).hasMessageContaining("Unable to fetch IDP token");
    }

    @Test
    void testAuthenticateInvalidJWT() throws Exception {
        // Given
        setupDefaultFrameworkProperties();
        setupMockHttpClient(HTTP_OK, createTokenResponse("not.a.valid.jwt"));

        WithUser withUser = mockWithUser("user", "pass");

        // When / Then
        assertThatThrownBy(() -> IdpTestAuthenticationSupport.authenticate(withUser)).isInstanceOf(
                NuxeoException.class).hasMessageContaining("Unable to parse IDP token");
    }

    @Test
    void testCurrentAuthenticationWhenNotSet() {
        // When
        JwtAuthenticationToken result = IdpTestAuthenticationSupport.currentAuthentication();

        // Then
        assertThat(result).isNull();
    }

    @Test
    void testBearerTokenWhenNotSet() {
        // When / Then
        assertThatThrownBy(IdpTestAuthenticationSupport::bearerToken).isInstanceOf(
                NuxeoException.class)
                .hasMessageContaining("No IDP token available for the current test context");
    }

    @Test
    void testBearerTokenOrNullWhenNotSet() {
        // When
        String result = IdpTestAuthenticationSupport.bearerTokenOrNull();

        // Then
        assertThat(result).isNull();
    }

    @Test
    void testAuthorizationHeaderValue() throws Exception {
        // Given
        String tokenValue = createValidJwtToken();
        setupDefaultFrameworkProperties();
        setupMockHttpClient(HTTP_OK, createTokenResponse(tokenValue));

        WithUser withUser = mockWithUser("user", "pass");
        IdpTestAuthenticationSupport.authenticate(withUser);

        // When
        String header = IdpTestAuthenticationSupport.authorizationHeaderValue();

        // Then
        assertThat(header).isEqualTo("Bearer " + tokenValue);
    }

    @Test
    void testClear() throws Exception {
        // Given
        String tokenValue = createValidJwtToken();
        setupDefaultFrameworkProperties();
        setupMockHttpClient(HTTP_OK, createTokenResponse(tokenValue));

        WithUser withUser = mockWithUser("user", "pass");
        IdpTestAuthenticationSupport.authenticate(withUser);
        assertThat(IdpTestAuthenticationSupport.currentAuthentication()).isNotNull();

        // When
        IdpTestAuthenticationSupport.clear();

        // Then
        assertThat(IdpTestAuthenticationSupport.currentAuthentication()).isNull();
        assertThat(IdpTestAuthenticationSupport.bearerTokenOrNull()).isNull();
    }

    @Test
    void testSpecialCharactersInCredentials() throws Exception {
        // Given
        String username = "user@domain.com";
        String password = "pass&word=special!";
        String tokenValue = createValidJwtToken();

        setupDefaultFrameworkProperties();
        setupMockHttpClient(HTTP_OK, createTokenResponse(tokenValue));

        WithUser withUser = mockWithUser(username, password);

        // When
        JwtAuthenticationToken result = IdpTestAuthenticationSupport.authenticate(withUser);

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    void testJwtParsingWithAllFields() throws Exception {
        // Given
        Instant now = Instant.now();
        String tokenValue = createJwtTokenWithClaims(now, now.plusSeconds(TOKEN_EXPIRY_SECONDS));

        setupDefaultFrameworkProperties();
        setupMockHttpClient(HTTP_OK, createTokenResponse(tokenValue));

        WithUser withUser = mockWithUser("user", "pass");

        // When
        JwtAuthenticationToken result = IdpTestAuthenticationSupport.authenticate(withUser);

        // Then
        Jwt jwt = result.getToken();
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isNotNull();
        assertThat(jwt.getHeaders()).isNotEmpty();
        assertThat(jwt.getClaims()).isNotEmpty();
    }

    @Test
    void testJwtParsingWithMissingDates() throws Exception {
        // Given
        String tokenValue = createJwtTokenWithoutDates();

        setupDefaultFrameworkProperties();
        setupMockHttpClient(HTTP_OK, createTokenResponse(tokenValue));

        WithUser withUser = mockWithUser("user", "pass");

        // When
        JwtAuthenticationToken result = IdpTestAuthenticationSupport.authenticate(withUser);

        // Then
        Jwt jwt = result.getToken();
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());
    }

    @Test
    void testHttpClientInjection() {
        // Given
        HttpClient customClient = mock(HttpClient.class);

        // When
        IdpTestAuthenticationSupport.setHttpClientForTesting(customClient);

        // Then - verify the custom client would be used (indirectly tested by other tests)
        // This test documents the injection capability
        assertThat(IdpTestAuthenticationSupport.httpClientOverride.get()).isEqualTo(customClient);
    }

    @Test
    void testClearResetsHttpClientOverride() throws Exception {
        // Given
        String tokenValue = createValidJwtToken();
        setupDefaultFrameworkProperties();
        setupMockHttpClient(HTTP_OK, createTokenResponse(tokenValue));

        WithUser withUser = mockWithUser("user", "pass");
        IdpTestAuthenticationSupport.authenticate(withUser);

        // Verify override is set
        assertThat(IdpTestAuthenticationSupport.httpClientOverride).isNotNull();

        // When
        IdpTestAuthenticationSupport.clear();

        // Then - both token context and HttpClient override should be cleared
        assertThat(IdpTestAuthenticationSupport.currentAuthentication()).isNull();
        assertThat(IdpTestAuthenticationSupport.httpClientOverride.get()).isNull();
    }

    // Helper methods
    private void setupDefaultFrameworkProperties() {
        setupFrameworkProperties(
            IdpTestAuthenticationSupport.DEFAULT_USERNAME,
            IdpTestAuthenticationSupport.DEFAULT_PASSWORD,
            IdpTestAuthenticationSupport.DEFAULT_TOKEN_URL,
            IdpTestAuthenticationSupport.DEFAULT_CLIENT_ID,
            IdpTestAuthenticationSupport.DEFAULT_CLIENT_SECRET,
            IdpTestAuthenticationSupport.DEFAULT_SCOPE
        );
    }

    private void setupFrameworkProperties(String username, String password, String tokenUrl,
            String clientId, String clientSecret, String scope) {
        setupFrameworkProperty("idp.test.username", "idp.username", username,
                IdpTestAuthenticationSupport.DEFAULT_USERNAME);
        setupFrameworkProperty("idp.test.password", "idp.password", password,
                IdpTestAuthenticationSupport.DEFAULT_PASSWORD);
        setupFrameworkProperty("idp.test.token", "idp.token", tokenUrl,
                IdpTestAuthenticationSupport.DEFAULT_TOKEN_URL);
        setupFrameworkProperty("idp.test.client-id", "idp.client-id", clientId,
                IdpTestAuthenticationSupport.DEFAULT_CLIENT_ID);
        setupFrameworkProperty("idp.test.client-secret", "idp.client-secret", clientSecret,
                IdpTestAuthenticationSupport.DEFAULT_CLIENT_SECRET);

        frameworkMock.when(() -> Framework.getProperty("idp.scope",
                IdpTestAuthenticationSupport.DEFAULT_SCOPE))
                .thenReturn(scope != null ? scope : IdpTestAuthenticationSupport.DEFAULT_SCOPE);
    }

    private void setupFrameworkProperty(String testKey, String fallbackKey, String value, String defaultValue) {
        String effectiveValue = value != null ? value : defaultValue;
        frameworkMock.when(() -> Framework.getProperty(testKey, defaultValue))
                .thenReturn(effectiveValue);
        frameworkMock.when(() -> Framework.getProperty(fallbackKey, defaultValue))
                .thenReturn(defaultValue);
    }

    private void setupMockHttpClient(int statusCode, String responseBody) throws Exception {
        // Mock the response with a fresh InputStream for each call
        mockResponse = mock(HttpResponse.class);
        InputStream inputStream = new ByteArrayInputStream(
                responseBody.getBytes(StandardCharsets.UTF_8));

        when(mockResponse.statusCode()).thenReturn(statusCode);
        when(mockResponse.body()).thenReturn(inputStream);
        when(mockHttpClient.send(any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);
    }

    private WithUser mockWithUser(String username, String password) {
        WithUser withUser = mock(WithUser.class);
        when(withUser.value()).thenReturn(username);
        when(withUser.password()).thenReturn(password);
        return withUser;
    }

    private String createTokenResponse(String token) {
        return String.format("{\"access_token\":\"%s\",\"token_type\":\"Bearer\"}", token);
    }

    private String createValidJwtToken() {
        Instant now = Instant.now();
        return createJwtTokenWithClaims(now, now.plusSeconds(TOKEN_EXPIRY_SECONDS));
    }

    private String createJwtTokenWithClaims(Instant issuedAt, Instant expiresAt) {
        // Create a minimal valid JWT for testing using PlainJWT (no signature required)
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("testuser")
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .claim("scope", "openid profile")
                .build();

        // Use PlainJWT which can be serialized without signing
        PlainJWT plainJWT = new PlainJWT(claimsSet);
        return plainJWT.serialize();
    }

    private String createJwtTokenWithoutDates() {
        // Create a minimal valid JWT without issue/expiry dates
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("testuser")
                .claim("scope", "openid profile")
                .build();

        // Use PlainJWT which can be serialized without signing
        PlainJWT plainJWT = new PlainJWT(claimsSet);
        return plainJWT.serialize();
    }
}
