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
package org.nuxeo.runtime.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for {@link MongoDBComponent} to verify that credentials are properly masked in log messages.
 */
public class MongoDBComponentTest {

    @ParameterizedTest(name = "{index} - {0}")
    @MethodSource("maskingCases")
    @SuppressWarnings("unused") // caseName is used only in the @ParameterizedTest display name
    public void testMaskSecrets(String caseName, String connectionString, String expectedMaskedConnectionString) {
        // when
        String actualMasked = MongoDBComponent.maskSecrets(connectionString);

        // then
        assertThat(actualMasked).isEqualTo(expectedMaskedConnectionString);
    }

    private static Stream<Arguments> maskingCases() {
        return Stream.of(
                Arguments.of(
                        "standard mongodb with credentials",
                        "mongodb://admin:password123@localhost:27017",
                        "mongodb://***:***@localhost:27017"),
                Arguments.of(
                        "mongodb+srv with credentials",
                        "mongodb+srv://dbUser:secret@cluster0.mongodb.net",
                        "mongodb+srv://***:***@cluster0.mongodb.net"),
                Arguments.of(
                        "complex password with special characters",
                        "mongodb://user:p@ssw0rd!#$%@host.example.com:27017",
                        "mongodb://***:***@host.example.com:27017"),
                Arguments.of(
                        "complex username with @ symbol",
                        "mongodb://user@domain.com:password@host.example.com:27017",
                        "mongodb://***:***@host.example.com:27017"),
                Arguments.of(
                        "url-encoded credentials",
                        "mongodb://user%40domain:pass%3Aword@host:27017",
                        "mongodb://***:***@host:27017"),
                Arguments.of(
                        "long connection string with replica set",
                        "mongodb://myUser:myPassword@host1:27017,host2:27017,host3:27017/mydb?replicaSet=myReplicaSet&ssl=true",
                        "mongodb://***:***@host1:27017,host2:27017,host3:27017/mydb?replicaSet=myReplicaSet&ssl=true"),
                Arguments.of(
                        "mongodb+srv with query parameters",
                        "mongodb+srv://admin:secretPass@cluster0.example.mongodb.net/myDatabase?retryWrites=true&w=majority",
                        "mongodb+srv://***:***@cluster0.example.mongodb.net/myDatabase?retryWrites=true&w=majority"),
                Arguments.of(
                        "no credentials - unchanged",
                        "mongodb://localhost:27017",
                        "mongodb://localhost:27017"),
                Arguments.of(
                        "no credentials srv - unchanged",
                        "mongodb+srv://cluster0.mongodb.net",
                        "mongodb+srv://cluster0.mongodb.net"),
                Arguments.of(
                        "very long password with special characters",
                        "mongodb://user:aVeryLongPasswordWith1234567890AndSpecialChars!@#$%^&*()@host:27017",
                        "mongodb://***:***@host:27017"),
                Arguments.of(
                        "minimal credentials",
                        "mongodb://u:p@host",
                        "mongodb://***:***@host"),
                Arguments.of(
                        "numeric credentials",
                        "mongodb://12345:67890@host:27017",
                        "mongodb://***:***@host:27017"),
                Arguments.of(
                        "unicode credentials",
                        "mongodb://用户:密码@host:27017",
                        "mongodb://***:***@host:27017"),
                Arguments.of(
                        "non-mongodb URL - unchanged",
                        "https://user:pass@example.com",
                        "https://user:pass@example.com"),
                Arguments.of(
                        "real-world DocumentDB connection string",
                        "mongodb://masteruser:MySecretPassword123!@docdb-cluster.cluster-abc123.us-east-1.docdb.amazonaws.com:27017/?tls=true&tlsCAFile=rds-combined-ca-bundle.pem&replicaSet=rs0&readPreference=secondaryPreferred&retryWrites=false",
                        "mongodb://***:***@docdb-cluster.cluster-abc123.us-east-1.docdb.amazonaws.com:27017/?tls=true&tlsCAFile=rds-combined-ca-bundle.pem&replicaSet=rs0&readPreference=secondaryPreferred&retryWrites=false")
        );
    }
}
