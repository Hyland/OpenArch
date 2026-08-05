/*
 * (C) Copyright 2014-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.runtime.mongodb;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

import javax.net.ssl.SSLContext;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.nuxeo.runtime.RuntimeServiceException;
import org.nuxeo.runtime.api.Framework;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;

/**
 * Helper for connection to the MongoDB server
 *
 * @since 9.1
 */
public class MongoDBConnectionHelper {

    private static final Logger log = LogManager.getLogger(MongoDBConnectionHelper.class);

    private static final String SERVER_PROP = "nuxeo.mongodb.server";

    private static final String DBNAME_PROP = "nuxeo.mongodb.dbname";

    private static final String SSL_PROP = "nuxeo.mongodb.ssl";

    private static final String TRUST_STORE_PATH_PROP = "nuxeo.mongodb.truststore.path";

    private static final String TRUST_STORE_PASSWORD_PROP = "nuxeo.mongodb.truststore.password";

    private static final String TRUST_STORE_TYPE_PROP = "nuxeo.mongodb.truststore.type";

    private static final String KEY_STORE_PATH_PROP = "nuxeo.mongodb.keystore.path";

    private static final String KEY_STORE_PASSWORD_PROP = "nuxeo.mongodb.keystore.password";

    private static final String KEY_STORE_TYPE_PROP = "nuxeo.mongodb.keystore.type";

    private static final String MAX_CONNECTION_IDLE_TIME_SEC_PROP = "nuxeo.mongodb.maxConnectionIdleTimeSec";

    private static final String MAX_CONNECTION_LIFE_TIME_SEC_PROP = "nuxeo.mongodb.maxConnectionLifeTimeSec";

    private static final String SERVER_DEFAULT = "localhost:27017";

    private static final String DBNAME_DEFAULT = "nuxeo";

    private static final String SSL_DEFAULT = "false";

    private static final String TRUST_STORE_PATH_DEFAULT = "";

    private static final String TRUST_STORE_PASSWORD_DEFAULT = "";

    private static final String TRUST_STORE_TYPE_DEFAULT = "JKS";

    private static final String KEY_STORE_PATH_DEFAULT = "";

    private static final String KEY_STORE_PASSWORD_DEFAULT = "";

    private static final String KEY_STORE_TYPE_DEFAULT = "JKS";

    private static final String PEM_CERTIFICATES_TYPE = "PEM";

    private static final int MONGODB_OPTION_CONNECT_TIMEOUT_MS = 30000;

    private static final int MONGODB_OPTION_READ_TIMEOUT_MS = 60000;

    private static final long MONGODB_OPTION_MAX_CONNECTION_IDLE_TIME_SEC_DEFAULT = 600L;

    private static final long MONGODB_OPTION_MAX_CONNECTION_LIFE_TIME_SEC_DEFAULT = 3600L;

    static final String KEY_ALIAS = "key";

    static final String CERT_ALIAS_PATTERN = "cert-%s";

    private MongoDBConnectionHelper() {
        // Empty
    }

    /**
     * Gets the configuration.
     */
    public static MongoDBConnectionConfig getMongoClientConfig() {
        MongoDBConnectionConfig config = new MongoDBConnectionConfig();
        config.server = prop(SERVER_PROP, SERVER_DEFAULT);
        config.dbname = prop(DBNAME_PROP, DBNAME_DEFAULT);
        config.ssl = prop(SSL_PROP, SSL_DEFAULT);
        config.trustStorePath = prop(TRUST_STORE_PATH_PROP, TRUST_STORE_PATH_DEFAULT);
        config.trustStorePassword = prop(TRUST_STORE_PASSWORD_PROP, TRUST_STORE_PASSWORD_DEFAULT);
        config.trustStoreType = prop(TRUST_STORE_TYPE_PROP, TRUST_STORE_TYPE_DEFAULT);
        config.keyStorePath = prop(KEY_STORE_PATH_PROP, KEY_STORE_PATH_DEFAULT);
        config.keyStorePassword = prop(KEY_STORE_PASSWORD_PROP, KEY_STORE_PASSWORD_DEFAULT);
        config.keyStoreType = prop(KEY_STORE_TYPE_PROP, KEY_STORE_TYPE_DEFAULT);
        config.maxConnectionIdleTimeSec = Framework.getLongProperty(MAX_CONNECTION_IDLE_TIME_SEC_PROP,
                MONGODB_OPTION_MAX_CONNECTION_IDLE_TIME_SEC_DEFAULT);
        config.maxConnectionLifeTimeSec = Framework.getLongProperty(MAX_CONNECTION_LIFE_TIME_SEC_PROP,
                MONGODB_OPTION_MAX_CONNECTION_LIFE_TIME_SEC_DEFAULT);
        return config;
    }

    private static String prop(String prop, String defaultValue) {
        String value = Framework.getProperty(prop);
        if (isBlank(value)) {
            value = defaultValue;
        }
        return value;
    }

    /**
     * Initializes a connection to the MongoDB server.
     *
     * @param config the MongoDB connection config
     * @return the MongoDB client
     * @since 10.3
     */
    public static MongoClient newMongoClient(MongoDBConnectionConfig config) {
        String server = config.server;
        if (StringUtils.isBlank(server)) {
            throw new RuntimeServiceException("Missing <server> in MongoDB descriptor");
        }
        MongoClientSettings.Builder settingsBuilder = MongoClientSettings.builder().applicationName("Nuxeo");
        SSLContext sslContext = getSSLContext(config);
        if (sslContext == null) {
            if (config.ssl != null) {
                settingsBuilder.applyToSslSettings(s -> s.enabled(Boolean.parseBoolean(config.ssl)));
            }
        } else {
            settingsBuilder.applyToSslSettings(s -> s.enabled(true).context(sslContext));
        }

        // don't wait forever by default when connecting
        settingsBuilder.applyToSocketSettings(s -> s.connectTimeout(MONGODB_OPTION_CONNECT_TIMEOUT_MS, MILLISECONDS)
                                                    .readTimeout(MONGODB_OPTION_READ_TIMEOUT_MS, MILLISECONDS));

        // set a max idle time to avoid keeping idle connections forever
        settingsBuilder.applyToConnectionPoolSettings(
                cp -> cp.maxConnectionIdleTime(config.maxConnectionIdleTimeSec, SECONDS)
                        .maxConnectionLifeTime(config.maxConnectionLifeTimeSec, SECONDS));

        if (server.startsWith("mongodb://") || server.startsWith("mongodb+srv://")) {
            // allow mongodb*:// URI syntax for the server, to pass everything in one string
            settingsBuilder.applyConnectionString(new ConnectionString(server));
        } else {
            settingsBuilder.applyToClusterSettings(b -> b.hosts(List.of(new ServerAddress(server))));
        }
        MongoClientSettings settings = settingsBuilder.build();
        MongoClient client = MongoClients.create(settings);
        log.debug("MongoClient initialized with settings: {}", settings);
        return client;
    }

    protected static SSLContext getSSLContext(MongoDBConnectionConfig config) {
        try {
            KeyStore trustStore = loadKeyStore(config.trustStorePath, config.trustStorePassword, config.trustStoreType);
            KeyStore keyStore = loadKeyStore(config.keyStorePath, config.keyStorePassword, config.keyStoreType);
            if (trustStore == null && keyStore == null) {
                return null;
            }
            SSLContextBuilder sslContextBuilder = SSLContexts.custom();
            if (trustStore != null) {
                sslContextBuilder.loadTrustMaterial(trustStore, null);
            }
            if (keyStore != null) {
                sslContextBuilder.loadKeyMaterial(keyStore,
                        StringUtils.isBlank(config.keyStorePassword) ? null : config.keyStorePassword.toCharArray());
            }
            return sslContextBuilder.build();
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeServiceException("Cannot setup SSL context: " + config, e);
        }
    }

    protected static KeyStore loadKeyStore(String path, String password, String type)
            throws GeneralSecurityException, IOException {
        if (StringUtils.isBlank(path)) {
            return null;
        }

        String keyStoreType = StringUtils.defaultIfBlank(type, KeyStore.getDefaultType());
        keyStoreType = keyStoreType.trim(); // normalize whitespace

        KeyStore keyStore;
        try (InputStream is = Files.newInputStream(Paths.get(path))) {
            if (PEM_CERTIFICATES_TYPE.equalsIgnoreCase(keyStoreType)) {
                log.debug("Loading PEM file from: {}", path);
                keyStore = loadPemKeyStore(is, password, path);
            } else {
                char[] passwordChars = StringUtils.isBlank(password) ? null : password.toCharArray();
                keyStore = KeyStore.getInstance(keyStoreType);
                keyStore.load(is, passwordChars);
                log.debug("Loaded {} keystore from: {}", keyStoreType, path);
            }
        }

        return keyStore;
    }

    /**
     * Loads a PEM file and converts it to a KeyStore.
     * Supports PEM files containing:
     * - CA certificates only (creates TrustStore)
     * - Client certificate + private key (creates KeyStore)
     * - Multiple certificates (certificate chain)
     *
     * @param fileStream the input stream of the PEM file
     * @param password the password for the keystore (used for private key protection)
     * @param path the file path (for logging purposes)
     * @return a KeyStore containing the parsed PEM content
     * @throws GeneralSecurityException if PEM parsing fails or no valid content found
     * @throws IOException if file reading fails
     */
    protected static KeyStore loadPemKeyStore(InputStream fileStream, String password, String path)
            throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null); // initialize empty keystore

        try (PEMParser pemParser = new PEMParser(new InputStreamReader(fileStream, StandardCharsets.UTF_8))) {
            PrivateKey privateKey = null;
            List<X509Certificate> certificates = new ArrayList<>();

            // Parse all objects from the PEM file
            Object obj;
            int certCount = 0;
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            while ((obj = pemParser.readObject()) != null) {
                if (obj instanceof PEMKeyPair pemKeyPair) {
                    KeyPair keyPair = converter.getKeyPair(pemKeyPair);
                    privateKey = keyPair.getPrivate();
                    log.debug("Found private key (PEMKeyPair) in PEM file");
                } else if (obj instanceof PrivateKeyInfo privateKeyInfo) {
                    privateKey = converter.getPrivateKey(privateKeyInfo);
                    log.debug("Found private key (PKCS#8) in PEM file");
                } else if (obj instanceof X509CertificateHolder x509CertificateHolder) {
                    X509Certificate cert = new JcaX509CertificateConverter().getCertificate(x509CertificateHolder);
                    certificates.add(cert);
                    certCount++;
                    log.debug("Found certificate #{} in PEM file: {}", certCount, cert.getSubjectX500Principal());
                }
            }

            // Private key must be accompanied by certificate chain for MongoDB
            if (privateKey != null && !certificates.isEmpty()) {
                // KeyStore with client certificate and private key
                char[] passwordChars = StringUtils.isBlank(password) ? null : password.toCharArray();
                keyStore.setKeyEntry(KEY_ALIAS, privateKey, passwordChars, certificates.toArray(new Certificate[0]));
                log.debug("Loaded keystore with private key and {} certificate(s)", certificates.size());
            } else if (!certificates.isEmpty()) {
                // TrustStore with CA certificates only
                for (int i = 0; i < certificates.size(); i++) {
                    X509Certificate cert = certificates.get(i);
                    String alias = CERT_ALIAS_PATTERN.formatted(i);
                    keyStore.setCertificateEntry(alias, cert);
                    log.debug("Added trusted certificate '{}': {}", alias, cert.getSubjectX500Principal());
                }
                log.debug("Loaded truststore with {} certificate(s)", certificates.size());
            } else {
                throw new GeneralSecurityException("Could not parse any key/cert from PEM file: " + path);
            }
        }

        return keyStore;
    }

    /**
     * Check if the collection exists and if it is not empty
     *
     * @param mongoDatabase the Mongo database
     * @param collection the collection name
     * @return true if the collection exists and not empty, false otherwise
     */
    public static boolean hasCollection(MongoDatabase mongoDatabase, String collection) {
        MongoIterable<String> collections = mongoDatabase.listCollectionNames();
        boolean found = StreamSupport.stream(collections.spliterator(), false).anyMatch(collection::equals);
        return found && mongoDatabase.getCollection(collection).estimatedDocumentCount() > 0;
    }
}
