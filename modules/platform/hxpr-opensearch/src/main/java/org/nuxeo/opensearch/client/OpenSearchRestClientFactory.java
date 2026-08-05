/*
 * (C) Copyright 2017-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.opensearch.client;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.net.ssl.SSLContext;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.opensearch.api.OpenSearchClient;
import org.nuxeo.runtime.api.Framework;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.jackson3.JacksonJsonProvider;
import org.opensearch.client.json.jackson3.JacksonJsonpMapper;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import jakarta.json.spi.JsonProvider;

/**
 * @since 9.3
 */
public class OpenSearchRestClientFactory {

    private static final Logger log = LogManager.getLogger(OpenSearchRestClientFactory.class);

    static final String ADDRESS_LIST_PROP = "opensearch.addressList";

    private static final String CONNECTION_TIMEOUT_MS_PROP = "opensearch.restClient.connectionTimeoutMs";

    private static final String SOCKET_TIMEOUT_MS_PROP = "opensearch.restClient.socketTimeoutMs";

    private static final String CONNECTION_POOL_TIMEOUT_MS_PROP = "opensearch.restClient.connectionPoolTimeoutMs";

    private static final String MAX_CONN_PER_ROUTE_PROP = "opensearch.restClient.maxConnPerRoute";

    private static final String MAX_CONN_TOTAL_PROP = "opensearch.restClient.maxConnTotal";

    static final String USERNAME_PROP = "opensearch.restClient.username";

    static final String PASSWORD_PROP = "opensearch.restClient.password";

    static final String TRUST_STORE_PATH_PROP = "opensearch.restClient.truststore.path";

    static final String TRUST_STORE_PASSWORD_PROP = "opensearch.restClient.truststore.password";

    static final String TRUST_STORE_TYPE_PROP = "opensearch.restClient.truststore.type";

    private static final String KEY_STORE_PATH_PROP = "opensearch.restClient.keystore.path";

    private static final String KEY_STORE_PASSWORD_PROP = "opensearch.restClient.keystore.password";

    private static final String KEY_STORE_TYPE_PROP = "opensearch.restClient.keystore.type";

    private static final String ADDRESS_LIST_DEFAULT = "http://localhost:9200";

    private static final String CONNECTION_TIMEOUT_MS_DEFAULT = "30000";

    private static final String SOCKET_TIMEOUT_MS_DEFAULT = "20000";

    private static final String CONNECTION_POOL_TIMEOUT_MS_DEFAULT = "10000";

    private static final String MAX_CONN_PER_ROUTE_DEFAULT = "10";

    private static final String MAX_CONN_TOTAL_DEFAULT = "30";

    private static final String TRUST_STORE_PATH_DEFAULT = "";

    private static final String TRUST_STORE_PASSWORD_DEFAULT = "";

    private static final String TRUST_STORE_TYPE_DEFAULT = "JKS";

    private static final String KEY_STORE_PATH_DEFAULT = "";

    private static final String KEY_STORE_PASSWORD_DEFAULT = "";

    private static final String KEY_STORE_TYPE_DEFAULT = "JKS";

    private static final JsonMapper OBJECT_MAPPER = JsonMapper.builder()
            .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            // Jackson 3 changed FAIL_ON_TRAILING_TOKENS default to true; must disable for JsonpMapper
            .configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, false)
            .build();

    @SuppressWarnings("resource") // factory for OpenSearchRestClient
    public OpenSearchClient create() {
        String addressList = prop(ADDRESS_LIST_PROP, ADDRESS_LIST_DEFAULT);
        if (addressList.isEmpty()) {
            throw new IllegalArgumentException("No addressList option provided cannot connect RestClient");
        }
        String[] hosts = addressList.split(",");
        HttpHost[] httpHosts = new HttpHost[hosts.length];
        int i = 0;
        for (String host : hosts) {
            try {
                httpHosts[i++] = HttpHost.create(host);
            } catch (URISyntaxException e) {
                throw new NuxeoException("Invalid OpenSearch host URI: " + host, e);
            }
        }
        Timeout connectTimeoutMs = Timeout.ofMilliseconds(
                Integer.parseInt(prop(CONNECTION_TIMEOUT_MS_PROP, CONNECTION_TIMEOUT_MS_DEFAULT)));
        Timeout responseTimeout = Timeout.ofMilliseconds(
                Integer.parseInt(prop(SOCKET_TIMEOUT_MS_PROP, SOCKET_TIMEOUT_MS_DEFAULT)));
        Timeout connectionPoolTimeoutMs = Timeout.ofMilliseconds(
                Integer.parseInt(prop(CONNECTION_POOL_TIMEOUT_MS_PROP, CONNECTION_POOL_TIMEOUT_MS_DEFAULT)));

        // Response timeout and connection request timeout stay on RequestConfig
        // (connect timeout has moved to ConnectionConfig in HttpClient 5.2+)
        ApacheHttpClient5TransportBuilder builder = ApacheHttpClient5TransportBuilder.builder(httpHosts)
                .setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                        .setResponseTimeout(responseTimeout)
                        .setConnectionRequestTimeout(connectionPoolTimeoutMs));

        addClientCallback(builder, connectTimeoutMs);
        builder.setMapper(newJsonpMapper());
        OpenSearchTransport transport = builder.build();
        return new OpenSearchRestClient(newJsonProvider(), newJsonpMapper(), transport);
    }

    protected JsonProvider newJsonProvider() {
        return new JacksonJsonProvider();
    }

    protected JsonpMapper newJsonpMapper() {
        return new JacksonJsonpMapper(OBJECT_MAPPER);
    }

    private void addClientCallback(ApacheHttpClient5TransportBuilder builder, Timeout connectTimeout) {
        boolean debugEnabled = log.isDebugEnabled();
        int maxConnPerRoute = Integer.parseInt(prop(MAX_CONN_PER_ROUTE_PROP, MAX_CONN_PER_ROUTE_DEFAULT));
        int maxConnTotal = Integer.parseInt(prop(MAX_CONN_TOTAL_PROP, MAX_CONN_TOTAL_DEFAULT));
        BasicCredentialsProvider credentialProvider = getCredentialProvider();
        SSLContext sslContext = getSslContext();

        // Always install our own connection manager so we can apply ConnectionConfig (connect timeout).
        // When a connection manager is installed via HttpClientConfigCallback, any value set through
        // setConnectionConfigCallback would be ignored, so the ConnectionConfig must be set directly
        // on the PoolingAsyncClientConnectionManagerBuilder.
        ConnectionConfig connectionConfig = ConnectionConfig.custom().setConnectTimeout(connectTimeout).build();

        builder.setHttpClientConfigCallback(httpClientBuilder -> {
            PoolingAsyncClientConnectionManagerBuilder connManagerBuilder =
                    PoolingAsyncClientConnectionManagerBuilder.create();
            if (sslContext != null) {
                connManagerBuilder.setTlsStrategy(
                        ClientTlsStrategyBuilder.create().setSslContext(sslContext).buildAsync());
            }
            connManagerBuilder.setMaxConnPerRoute(maxConnPerRoute);
            connManagerBuilder.setMaxConnTotal(maxConnTotal);
            connManagerBuilder.setDefaultConnectionConfig(connectionConfig);
            httpClientBuilder.setConnectionManager(connManagerBuilder.build());

            if (credentialProvider != null) {
                httpClientBuilder.setDefaultCredentialsProvider(credentialProvider);
            }

            // Trace all requests if debug is enabled
            if (debugEnabled) {
                httpClientBuilder.addRequestInterceptorLast(this::traceOpenSearchRequest);
            }
            return httpClientBuilder;
        });
    }

    /**
     * Trace OpenSearch request if debug is enabled
     */
    void traceOpenSearchRequest(HttpRequest request, EntityDetails entity, HttpContext context) throws IOException {
        if (request instanceof ClassicHttpRequest classicRequest) {
            HttpEntity httpEntity = classicRequest.getEntity();
            if (httpEntity != null) {
                String requestBody;
                try (InputStream inputStream = httpEntity.getContent()) {
                    requestBody = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                }
                String method = request.getMethod();
                String uri = request.getRequestUri();
                log.debug("OpenSearch Request {}, Method: '{}' request {}", uri, method, requestBody);
            }
        }
    }

    private BasicCredentialsProvider getCredentialProvider() {
        String user = prop(USERNAME_PROP, "");
        if (StringUtils.isBlank(user)) {
            return null;
        }
        String password = prop(PASSWORD_PROP, "");
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(null, -1),
                new UsernamePasswordCredentials(user, password.toCharArray()));
        return credentialsProvider;
    }

    private SSLContext getSslContext() {
        String trustStorePath = prop(TRUST_STORE_PATH_PROP, TRUST_STORE_PATH_DEFAULT);
        String trustStorePassword = prop(TRUST_STORE_PASSWORD_PROP, TRUST_STORE_PASSWORD_DEFAULT);
        String trustStoreType = prop(TRUST_STORE_TYPE_PROP, TRUST_STORE_TYPE_DEFAULT);
        String keyStorePath = prop(KEY_STORE_PATH_PROP, KEY_STORE_PATH_DEFAULT);
        String keyStorePassword = prop(KEY_STORE_PASSWORD_PROP, KEY_STORE_PASSWORD_DEFAULT);
        String keyStoreType = prop(KEY_STORE_TYPE_PROP, KEY_STORE_TYPE_DEFAULT);
        try {
            KeyStore trustStore = loadKeyStore(trustStorePath, trustStorePassword, trustStoreType);
            KeyStore keyStore = loadKeyStore(keyStorePath, keyStorePassword, keyStoreType);
            if (trustStore == null && keyStore == null) {
                return null;
            }
            SSLContextBuilder sslContextBuilder = SSLContexts.custom();
            if (trustStore != null) {
                sslContextBuilder.loadTrustMaterial(trustStore, null);
            }
            if (keyStore != null) {
                sslContextBuilder.loadKeyMaterial(keyStore,
                        StringUtils.isBlank(keyStorePassword) ? null : keyStorePassword.toCharArray());
            }
            return sslContextBuilder.build();
        } catch (GeneralSecurityException | IOException e) {
            throw new NuxeoException("Cannot setup SSL for RestClient", e);
        }
    }

    private KeyStore loadKeyStore(String path, String password, String type)
            throws GeneralSecurityException, IOException {
        if (StringUtils.isBlank(path)) {
            return null;
        }
        String keyStoreType = StringUtils.defaultIfBlank(type, KeyStore.getDefaultType());
        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
        char[] passwordChars = StringUtils.isBlank(password) ? null : password.toCharArray();
        try (InputStream is = Files.newInputStream(Paths.get(path))) {
            keyStore.load(is, passwordChars);
        }
        return keyStore;
    }

    private static String prop(String prop, String defaultValue) {
        String value = Framework.getProperty(prop);
        if (isBlank(value)) {
            value = defaultValue;
        }
        return value;
    }

}
