package org.nuxeo.runtime.springboot.junit.security;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.local.WithUser;
import org.nuxeo.runtime.api.Framework;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;

/**
 * Helper that retrieves real IDP tokens (password grant) for tests and exposes them for reuse.
 *
 * <p>This class uses a singleton pattern with dependency injection support for testing.
 * Tests can provide a custom HttpClient via {@link #setHttpClientForTesting(HttpClient)}
 * to mock HTTP interactions without reflection.
 */
public final class IdpTestAuthenticationSupport {

    private static final String TOKEN_URL_PROPERTY = "idp.test.token";
    private static final String USERNAME_PROPERTY = "idp.test.username";
    private static final String PASSWORD_PROPERTY = "idp.test.password";
    private static final String CLIENT_ID_PROPERTY = "idp.test.client-id";
    private static final String CLIENT_SECRET_PROPERTY = "idp.test.client-secret";
    private static final String SCOPE_PROPERTY = "idp.scope";

    public static final String DEFAULT_USERNAME_ID = "ff7b0360-3594-416c-b587-2fa4dd0839ce";
    public static final String DEFAULT_USERNAME = "testuser";
    public static final String DEFAULT_TOKEN_URL = "http://127.0.0.1:5002/idp/connect/token";
    public static final String DEFAULT_PASSWORD = "password";
    public static final String DEFAULT_CLIENT_ID = "nuxeo-client";
    public static final String DEFAULT_CLIENT_SECRET = "secret";
    public static final String DEFAULT_SCOPE = "openid profile hxp hxpr";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient DEFAULT_HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final ThreadLocal<TokenContext> CURRENT_TOKEN = new ThreadLocal<>();

    // Package-private for testing - allows test injection without reflection
    static final AtomicReference<HttpClient> httpClientOverride = new AtomicReference<>();

    private IdpTestAuthenticationSupport() {
        // utility
    }
    /**
     * Sets a custom HttpClient for testing purposes. This allows tests to inject
     * mock HttpClients without using reflection.
     *
     * @param httpClient the HttpClient to use, or null to reset to default
     */
    static void setHttpClientForTesting(HttpClient httpClient) {
        httpClientOverride.set(httpClient);
    }
    /**
     * Gets the active HttpClient - either the test override or the default.
     */
    private static HttpClient getHttpClient() {
        HttpClient override = httpClientOverride.get();
        return override != null ? override : DEFAULT_HTTP_CLIENT;
    }

    public static JwtAuthenticationToken authenticate(WithUser withUser) {
        String username = resolveUsername(withUser);
        String password = resolvePassword(withUser);
        String tokenValue = requestToken(username, password);
        Jwt jwt = parseJwt(tokenValue);
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, Collections.emptyList(), jwt.getSubject());
        CURRENT_TOKEN.set(new TokenContext(tokenValue, authentication));
        return authentication;
    }

    public static JwtAuthenticationToken currentAuthentication() {
        TokenContext ctx = CURRENT_TOKEN.get();
        return ctx == null ? null : ctx.authentication();
    }

    public static String bearerToken() {
        TokenContext ctx = CURRENT_TOKEN.get();
        if (ctx == null) {
            throw new NuxeoException("No IDP token available for the current test context");
        }
        return ctx.token();
    }

    public static String bearerTokenOrNull() {
        TokenContext ctx = CURRENT_TOKEN.get();
        return ctx == null ? null : ctx.token();
    }

    public static String authorizationHeaderValue() {
        return "Bearer " + bearerToken();
    }
    public static void clear() {
        CURRENT_TOKEN.remove();
        httpClientOverride.set(null);
    }

    private static String resolveUsername(WithUser withUser) {
        String fallback = Framework.getProperty(USERNAME_PROPERTY,
                Framework.getProperty("idp.username", DEFAULT_USERNAME));
        if (withUser == null || StringUtils.isBlank(withUser.value())) {
            return fallback;
        }
        return withUser.value();
    }

    private static String resolvePassword(WithUser withUser) {
        String fallback = Framework.getProperty(PASSWORD_PROPERTY,
                Framework.getProperty("idp.password", DEFAULT_PASSWORD));
        if (withUser == null || StringUtils.isBlank(withUser.password())) {
            return fallback;
        }
        return withUser.password();
    }

    private static String requestToken(String username, String password) {
        try {
            String formBody = buildFormBody(username, password);
            String tokenUrl = Framework.getProperty(TOKEN_URL_PROPERTY,
                    Framework.getProperty("idp.token", DEFAULT_TOKEN_URL));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();
            HttpResponse<InputStream> response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw new NuxeoException("IDP token endpoint returned status " + response.statusCode());
            }
            JsonNode tokenNode;
            try (InputStream body = response.body()) {
                JsonNode json = OBJECT_MAPPER.readTree(body);
                tokenNode = json.get("access_token");
            }
            if (tokenNode == null || StringUtils.isBlank(tokenNode.asText())) {
                throw new NuxeoException("IDP response does not contain an access_token");
            }
            return tokenNode.asText();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NuxeoException("Interrupted while fetching IDP token", e);
        } catch (IOException e) {
            throw new NuxeoException("Unable to fetch IDP token", e);
        }
    }

    private static String buildFormBody(String username, String password) {
        return "grant_type=" + urlEncode("password") // NOSONAR: not the same as DEFAULT_PASSWORD
                + "&scope=" + urlEncode(Framework.getProperty(SCOPE_PROPERTY, DEFAULT_SCOPE))
                + "&client_id=" + urlEncode(Framework.getProperty(CLIENT_ID_PROPERTY,
                    Framework.getProperty("idp.client-id", DEFAULT_CLIENT_ID)))
                + "&client_secret=" + urlEncode(Framework.getProperty(CLIENT_SECRET_PROPERTY,
                    Framework.getProperty("idp.client-secret", DEFAULT_CLIENT_SECRET)))
                + "&username=" + urlEncode(username)
                + "&password=" + urlEncode(password);
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static Jwt parseJwt(String tokenValue) {
        try {
            // Parse using JWTParser which handles both signed and unsigned JWTs
            JWT jwt = JWTParser.parse(tokenValue);
            JWTClaimsSet claimsSet = jwt.getJWTClaimsSet();

            // Use the common JWT interface - works for both SignedJWT and PlainJWT
            Map<String, Object> headers = new HashMap<>(jwt.getHeader().toJSONObject());
            Map<String, Object> claims = new HashMap<>(claimsSet.getClaims());
            Date issuedAt = claimsSet.getIssueTime();
            Date expiresAt = claimsSet.getExpirationTime();
            Instant issued = issuedAt == null ? Instant.now() : issuedAt.toInstant();
            Instant expires = expiresAt == null ? issued.plusSeconds(3600) : expiresAt.toInstant();
            return new Jwt(tokenValue, issued, expires, headers, claims);
        } catch (Exception e) {
            throw new NuxeoException("Unable to parse IDP token", e);
        }
    }

    private record TokenContext(String token, JwtAuthenticationToken authentication) {
    }
}
