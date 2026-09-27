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
package com.hyland.openarch.runtime.mongodb;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static com.hyland.openarch.runtime.mongodb.MongoDBConnectionHelper.CERT_ALIAS_PATTERN;
import static com.hyland.openarch.runtime.mongodb.MongoDBConnectionHelper.KEY_ALIAS;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.UnrecoverableKeyException;
import java.util.Objects;
import javax.net.ssl.SSLContext;

import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.connection.ClusterDescription;
import com.mongodb.connection.ServerDescription;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.hyland.openarch.runtime.RuntimeServiceException;

/**
 * Tests for {@link MongoDBConnectionHelper}.
 */
public class MongoDBConnectionHelperTest {

    private static final String JKS_TYPE = "JKS";
    private static final String PEM_TYPE = "PEM";
    private static final String PKCS12_TYPE = "PKCS12";

    @TempDir
    Path tempDir;

    @Test
    public void testGetSSLContextWithNoStores() {
        // given
        MongoDBConnectionConfig config = new MongoDBConnectionConfig();
        config.trustStorePath = "";
        config.keyStorePath = "";

        // when
        SSLContext actualSslContext = MongoDBConnectionHelper.getSSLContext(config);

        // then
        assertThat(actualSslContext).isNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "   " })
    public void testLoadKeyStoreWithEmptyPath(String storePath) throws Exception {
        // when
        KeyStore actualKeyStore = MongoDBConnectionHelper.loadKeyStore(storePath, "password", JKS_TYPE);

        // then
        assertThat(actualKeyStore).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "This is not a valid PEM file\nJust some random text" })
    public void testLoadPemKeyStoreWithInvalidContent(String invalidPem) throws Exception {
        // given
        Path truststorePath = tempDir.resolve("test.pem");
        try (var os = Files.newOutputStream(truststorePath)) {
            os.write(invalidPem.getBytes(StandardCharsets.UTF_8));
        }

        // when
        Exception actualException = catchException(() -> MongoDBConnectionHelper.loadKeyStore(truststorePath.toString(), "password", PEM_TYPE));

        // then
        assertThat(actualException)
                .isInstanceOf(GeneralSecurityException.class)
                .hasMessageContaining("Could not parse any key/cert from PEM file");
    }

    @Test
    public void testLoadPemKeyStoreWithCACertificateOnly() throws Exception {
        // given
        Path caCertPath = Path.of("src/test/resources/test-certs/ca-cert.pem");

        // when
        KeyStore actualKeyStore = MongoDBConnectionHelper.loadKeyStore(caCertPath.toString(), "password", PEM_TYPE);

        // then
        assertThat(actualKeyStore)
                .isNotNull()
                .satisfies(store -> {
                    String alias = CERT_ALIAS_PATTERN.formatted(0);
                    assertThat(store.size()).isGreaterThan(0);
                    assertThat(store.getType()).isEqualToIgnoringCase(PKCS12_TYPE);
                    assertThat(store.containsAlias(alias))
                            .as("Should contain certificate with alias '%s'", alias)
                            .isTrue();
                    assertThat(store.isCertificateEntry(alias))
                            .as("Entry should be a certificate entry")
                            .isTrue();
                    assertThat(store.getCertificate(alias))
                            .as("Should be able to retrieve certificate")
                            .isNotNull();
                });
    }

    @Test
    public void testLoadPemKeyStoreWithClientCertificateAndKey() throws Exception {
        // given
        Path combinedPemPath = Path.of("src/test/resources/test-certs/client-combined.pem");
        String password = "password";

        // when
        KeyStore actualKeyStore = MongoDBConnectionHelper.loadKeyStore(combinedPemPath.toString(), password, PEM_TYPE);

        //then
        String alias = KEY_ALIAS;
        assertThat(actualKeyStore)
                .isNotNull()
                .satisfies(store -> {
                    assertThat(store.size()).isGreaterThan(0);
                    assertThat(store.getType()).isEqualToIgnoringCase(PKCS12_TYPE);
                    assertThat(store.containsAlias(alias))
                            .as("Should contain key entry with alias '%s'", alias)
                            .isTrue();
                    assertThat(store.isKeyEntry(alias))
                            .as("Entry should be a key entry")
                            .isTrue();
                    assertThat(store.getKey(alias, password.toCharArray()))
                            .as("Should be able to retrieve private key")
                            .isNotNull();
                    assertThat(store.getCertificateChain(alias))
                            .as("Should have certificate chain for key entry")
                            .isNotNull();
                });
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", " " })
    public void testLoadPemKeyStoreWithEmptyPassword(String password) throws Exception {
        // given
        Path combinedPemPath = Path.of("src/test/resources/test-certs/client-combined.pem");

        // when
        KeyStore actualKeyStore = MongoDBConnectionHelper.loadKeyStore(combinedPemPath.toString(), password, PEM_TYPE);

        // then
        assertThat(actualKeyStore).isNotNull();
        assertThat(actualKeyStore.getKey(KEY_ALIAS, null))
                .isNotNull();
    }

    @Test
    public void testLoadPemKeyStoreWithInvalidPassword() throws Exception {
        // given
        Path combinedPemPath = Path.of("src/test/resources/test-certs/client-combined.pem");
        String correctPassword = "password";
        String incorrectPassword = "incorrect-password";
        KeyStore keyStore = MongoDBConnectionHelper.loadKeyStore(combinedPemPath.toString(), correctPassword, PEM_TYPE);

        // when
        Exception actualException = catchException(() -> Objects.requireNonNull(keyStore).getKey(KEY_ALIAS, incorrectPassword.toCharArray()));

        // then
        assertThat(actualException)
                .isNotNull()
                .isInstanceOf(UnrecoverableKeyException.class);
    }

    @Test
    public void testLoadKeyStoreWithJKSType() throws Exception {
        // given
        Path keystorePath = createTempKeystore("test.jks", JKS_TYPE, "password");

        // when
        KeyStore actualKeyStore = MongoDBConnectionHelper.loadKeyStore(keystorePath.toString(), "password", JKS_TYPE);

        // then
        assertThat(actualKeyStore)
                .isNotNull()
                .extracting(KeyStore::getType)
                .isEqualTo(JKS_TYPE);
    }

    @Test
    public void testLoadKeyStoreWithDefaultTypeWhenNull() throws Exception {
        // given
        Path keystorePath = createTempKeystore("test-default.jks", KeyStore.getDefaultType(), "password");

        // when
        KeyStore actualKeyStore = MongoDBConnectionHelper.loadKeyStore(keystorePath.toString(), "password", null);

        // then
        assertThat(actualKeyStore)
                .isNotNull()
                .extracting(KeyStore::getType)
                .isEqualTo(KeyStore.getDefaultType());
    }

    @Test
    public void testLoadJKSKeyStoreWithInvalidPassword() throws Exception {
        // given
        Path keystorePath = createTempKeystore("test-password.jks", JKS_TYPE, "password");

        // when
        Exception actualException = catchException(() -> MongoDBConnectionHelper.loadKeyStore(keystorePath.toString(), "invalid-password", JKS_TYPE));

        // then
        assertThat(actualException)
                .isNotNull()
                .isInstanceOf(IOException.class)
                .hasMessageContaining("password was incorrect")
                .cause()
                .isInstanceOf(UnrecoverableKeyException.class);
    }

    @Test
    public void testLoadJKSTrustStore() throws Exception {
        // given
        Path truststorePath = Path.of("src/test/resources/test-certs/truststore.jks");

        // when
        KeyStore actualKeyStore = MongoDBConnectionHelper.loadKeyStore(truststorePath.toString(), "changeit", JKS_TYPE);

        // then
        assertThat(actualKeyStore)
                .isNotNull()
                .satisfies(store -> {
                    String alias = "ca-cert";
                    assertThat(store.getType()).isEqualTo(JKS_TYPE);
                    assertThat(store.size()).isEqualTo(1);
                    assertThat(store.containsAlias(alias))
                            .as("Should contain CA certificate with alias '%s'", alias)
                            .isTrue();
                    assertThat(store.isCertificateEntry(alias))
                            .as("Entry should be a certificate entry")
                            .isTrue();
                    assertThat(store.getCertificate(alias))
                            .as("Should be able to retrieve CA certificate")
                            .isNotNull();
                });
    }

    @Test
    public void testLoadJKSKeyStore() throws Exception {
        // given
        Path keystorePath = Path.of("src/test/resources/test-certs/keystore.jks");
        String password = "changeit";

        // when
        KeyStore actualKeyStore = MongoDBConnectionHelper.loadKeyStore(keystorePath.toString(), password, JKS_TYPE);

        // then
        String alias = "client-key";
        assertThat(actualKeyStore)
                .isNotNull()
                .satisfies(store -> {
                    assertThat(store.getType()).isEqualTo(JKS_TYPE);
                    assertThat(store.size()).isEqualTo(1);
                    assertThat(store.containsAlias(alias))
                            .as("Should contain key entry with alias '%s'", alias)
                            .isTrue();
                    assertThat(store.isKeyEntry(alias))
                            .as("Entry should be a key entry")
                            .isTrue();
                    assertThat(store.getKey(alias, password.toCharArray()))
                            .as("Should be able to retrieve private key")
                            .isNotNull();
                    assertThat(store.getCertificateChain(alias))
                            .as("Should have certificate chain for key entry")
                            .isNotNull()
                            .hasSizeGreaterThan(0);
                });
    }

    @Test
    public void testNewMongoClientWithConnectionString() {
        // given
        MongoDBConnectionConfig config = new MongoDBConnectionConfig();
        config.server = "mongodb://localhost:27017";

        // when
        try (var actualClient = MongoDBConnectionHelper.newMongoClient(config)) {

            // then
            assertThat(actualClient)
                    .isNotNull()
                    .extracting(MongoClient::getClusterDescription)
                    .extracting(ClusterDescription::getServerDescriptions, as(LIST))
                    .hasSize(1)
                    .first(as(type(ServerDescription.class)))
                    .extracting(ServerDescription::getAddress)
                    .extracting(ServerAddress::toString)
                    .isEqualTo("localhost:27017");
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "   " })
    @SuppressWarnings("resource") // Testing exception case, Mongo client not created
    public void testNewMongoClientWithEmptyServer(String server) {
        // given
        MongoDBConnectionConfig config = new MongoDBConnectionConfig();
        config.server = server;

        // when
        Exception actualException = catchException(() -> MongoDBConnectionHelper.newMongoClient(config));

        // then
        assertThat(actualException)
                .isInstanceOf(RuntimeServiceException.class)
                .hasMessageContaining("Missing <server>");
    }

    @Test
    public void testNewMongoClientWithValidPEMTrustStore() {
        // given
        MongoDBConnectionConfig config = new MongoDBConnectionConfig();
        config.server = "localhost:27017";
        config.trustStorePath = "src/test/resources/test-certs/ca-cert.pem";
        config.trustStoreType = PEM_TYPE;

        // when
        try (var actualClient = MongoDBConnectionHelper.newMongoClient(config)) {

            // then
            assertThat(actualClient).as("MongoClient should be created with valid PEM truststore").isNotNull();
        }
    }

    @Test
    public void testNewMongoClientWithValidPEMKeyStore() {
        // given
        MongoDBConnectionConfig config = new MongoDBConnectionConfig();
        config.server = "localhost:27017";
        config.keyStorePath = "src/test/resources/test-certs/client-combined.pem";
        config.keyStorePassword = "password";
        config.keyStoreType = PEM_TYPE;

        // when
        try (var actualClient = MongoDBConnectionHelper.newMongoClient(config)) {

            // then
            assertThat(actualClient).as("MongoClient should be created with valid PEM keystore").isNotNull();
        }
    }

    @Test
    public void testNewMongoClientWithBothPEMStores() {
        // given
        MongoDBConnectionConfig config = new MongoDBConnectionConfig();
        config.server = "localhost:27017";
        config.trustStorePath = "src/test/resources/test-certs/ca-cert.pem";
        config.trustStoreType = PEM_TYPE;
        config.keyStorePath = "src/test/resources/test-certs/client-combined.pem";
        config.keyStorePassword = "password";
        config.keyStoreType = PEM_TYPE;

        // when
        try (var actualClient = MongoDBConnectionHelper.newMongoClient(config)) {

            // then
            assertThat(actualClient).as("MongoClient should be created with both PEM stores for mTLS").isNotNull();
        }
    }

    @Test
    public void testNewMongoClientWithJKSTrustStore() {
        // given
        MongoDBConnectionConfig config = new MongoDBConnectionConfig();
        config.server = "localhost:27017";
        config.trustStorePath = "src/test/resources/test-certs/truststore.jks";
        config.trustStorePassword = "changeit";
        config.trustStoreType = JKS_TYPE;

        // when
        try (var actualClient = MongoDBConnectionHelper.newMongoClient(config)) {

            // then
            assertThat(actualClient).as("MongoClient should be created with JKS truststore").isNotNull();
        }
    }

    @Test
    public void testNewMongoClientWithJKSKeyStore() {
        // given
        MongoDBConnectionConfig config = new MongoDBConnectionConfig();
        config.server = "localhost:27017";
        config.keyStorePath = "src/test/resources/test-certs/keystore.jks";
        config.keyStorePassword = "changeit";
        config.keyStoreType = JKS_TYPE;

        // when
        try (var actualClient = MongoDBConnectionHelper.newMongoClient(config)) {

            // then
            assertThat(actualClient).as("MongoClient should be created with JKS keystore").isNotNull();
        }
    }

    @Test
    public void testNewMongoClientWithBothJKSStores() {
        // given
        MongoDBConnectionConfig config = new MongoDBConnectionConfig();
        config.server = "localhost:27017";
        config.trustStorePath = "src/test/resources/test-certs/truststore.jks";
        config.trustStorePassword = "changeit";
        config.trustStoreType = JKS_TYPE;
        config.keyStorePath = "src/test/resources/test-certs/keystore.jks";
        config.keyStorePassword = "changeit";
        config.keyStoreType = JKS_TYPE;

        // when
        try (var actualClient = MongoDBConnectionHelper.newMongoClient(config)) {

            // then
            assertThat(actualClient).as("MongoClient should be created with both JKS stores for mTLS").isNotNull();
        }
    }

    @Test
    public void testNewMongoClientWithMixedStoreTypes() {
        // given - PEM truststore with JKS keystore
        MongoDBConnectionConfig config = new MongoDBConnectionConfig();
        config.server = "localhost:27017";
        config.trustStorePath = "src/test/resources/test-certs/ca-cert.pem";
        config.trustStoreType = PEM_TYPE;
        config.keyStorePath = "src/test/resources/test-certs/keystore.jks";
        config.keyStorePassword = "changeit";
        config.keyStoreType = JKS_TYPE;

        // when
        try (var actualClient = MongoDBConnectionHelper.newMongoClient(config)) {

            // then
            assertThat(actualClient).as("MongoClient should be created with mixed store types (PEM trust, JKS key)").isNotNull();
        }
    }

    @Test
    @SuppressWarnings("resource") // Testing exception case, Mongo client not created
    public void testNewMongoClientWithNonExistentFile() {
        // given
        Path nonExistentPath = tempDir.resolve("does-not-exist.jks");
        MongoDBConnectionConfig config = new MongoDBConnectionConfig();
        config.server = "localhost:27017";
        config.trustStorePath = nonExistentPath.toString();

        // when
        Exception actualException = catchException(() -> MongoDBConnectionHelper.newMongoClient(config));

        // then
        assertThat(actualException)
                .isInstanceOf(RuntimeServiceException.class)
                .cause()
                .isInstanceOf(NoSuchFileException.class)
                .hasMessageContaining(nonExistentPath.toString());
    }

    @SuppressWarnings("SameParameterValue")
    private Path createTempKeystore(String fileName, String type, String password) throws Exception {
        Path keystorePath = tempDir.resolve(fileName);
        KeyStore tempKeyStore = KeyStore.getInstance(type);
        tempKeyStore.load(null, null);
        try (var os = Files.newOutputStream(keystorePath)) {
            tempKeyStore.store(os, password.toCharArray());
        }

        return keystorePath;
    }
}
