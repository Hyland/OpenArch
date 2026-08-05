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
package org.nuxeo.opensearch.client;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.nuxeo.opensearch.client.OpenSearchRestClientFactory.ADDRESS_LIST_PROP;
import static org.nuxeo.opensearch.client.OpenSearchRestClientFactory.PASSWORD_PROP;
import static org.nuxeo.opensearch.client.OpenSearchRestClientFactory.TRUST_STORE_PASSWORD_PROP;
import static org.nuxeo.opensearch.client.OpenSearchRestClientFactory.TRUST_STORE_PATH_PROP;
import static org.nuxeo.opensearch.client.OpenSearchRestClientFactory.TRUST_STORE_TYPE_PROP;
import static org.nuxeo.opensearch.client.OpenSearchRestClientFactory.USERNAME_PROP;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.opensearch.api.OpenSearchClient;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.test.runner.RuntimeFeature;
import org.nuxeo.runtime.test.runner.WithFrameworkProperty;

/**
 * Tests for {@link OpenSearchRestClientFactory#create()} that exercise the different configuration branches driven by
 * {@link org.nuxeo.runtime.api.Framework#getProperty(String)}. Runs on top of a minimal Nuxeo runtime so that
 * {@link WithFrameworkProperty} can be used to set properties declaratively.
 */
@ExtendWith(FeaturesExtension.class)
@Features(RuntimeFeature.class)
public class TestOpenSearchRestClientFactory {

    private static final String TRUSTSTORE_PASSWORD = "changeit";

    @TempDir
    static Path tempDir;

    private static Path emptyTruststorePath;

    private final OpenSearchRestClientFactory factory = new OpenSearchRestClientFactory();

    @BeforeAll
    static void createEmptyTruststore() throws KeyStoreException, IOException, NoSuchAlgorithmException,
            CertificateException {
        // A valid but empty PKCS12 keystore is enough to exercise the SSL branch of addClientCallback:
        // the factory only needs to produce a non-null SSLContext from it.
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, TRUSTSTORE_PASSWORD.toCharArray());
        emptyTruststorePath = tempDir.resolve("truststore.p12");
        try (var out = Files.newOutputStream(emptyTruststorePath)) {
            keyStore.store(out, TRUSTSTORE_PASSWORD.toCharArray());
        }
    }

    @Test
    public void testCreateWithDefaults() {
        // No properties set: all defaults apply. localhost:9200 does not need to be reachable,
        // the transport is created lazily on first request.
        OpenSearchClient osClient = factory.create();
        assertNotNull(osClient);
    }

    @Test
    @WithFrameworkProperty(name = ADDRESS_LIST_PROP, value = "http://bad host:9200")
    public void testCreateWrapsInvalidHostUri() {
        // A hostname containing a space cannot be parsed by HttpHost.create and must be wrapped
        // in a NuxeoException.
        NuxeoException e = assertThrows(NuxeoException.class, factory::create);
        assertInstanceOf(URISyntaxException.class, e.getCause());
    }

    @Test
    @WithFrameworkProperty(name = USERNAME_PROP, value = "admin")
    @WithFrameworkProperty(name = PASSWORD_PROP, value = "secret")
    public void testCreateWithCredentials() {
        // Covers getCredentialProvider (non-blank user branch) and the credential branch of addClientCallback.
        OpenSearchClient osClient = factory.create();
        assertNotNull(osClient);
    }

    @Test
    public void testCreateWithTruststore() {
        // Covers the SSL branch of addClientCallback (sslContext != null).
        // Properties are set programmatically because @WithFrameworkProperty cannot reference a runtime-generated path.
        Framework.getProperties().put(TRUST_STORE_PATH_PROP, emptyTruststorePath.toString());
        Framework.getProperties().put(TRUST_STORE_PASSWORD_PROP, TRUSTSTORE_PASSWORD);
        Framework.getProperties().put(TRUST_STORE_TYPE_PROP, "PKCS12");
        try {
            OpenSearchClient osClient = factory.create();
            assertNotNull(osClient);
        } finally {
            Framework.getProperties().remove(TRUST_STORE_PATH_PROP);
            Framework.getProperties().remove(TRUST_STORE_PASSWORD_PROP);
            Framework.getProperties().remove(TRUST_STORE_TYPE_PROP);
        }
    }

    @Test
    @WithFrameworkProperty(name = TRUST_STORE_PATH_PROP, value = "/does/not/exist.jks")
    public void testCreateFailsOnMissingTruststore() {
        // Covers the error branch of getSslContext() wrapping IOException into NuxeoException.
        NuxeoException e = assertThrows(NuxeoException.class, factory::create);
        assertNotNull(e.getCause());
    }
}
