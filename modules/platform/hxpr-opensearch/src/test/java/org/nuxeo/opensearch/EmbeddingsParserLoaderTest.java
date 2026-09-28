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
package org.nuxeo.opensearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.RepositoryException;
import com.hyland.openarch.ecm.core.model.EmbeddingModel;
import com.hyland.openarch.runtime.api.Framework;
import org.nuxeo.opensearch.api.OpenSearchService;
import org.nuxeo.opensearch.config.EmbeddingsParserDescriptor;
import org.nuxeo.opensearch.io.EmbeddingsParser;
import org.nuxeo.opensearch.io.ParquetEmbeddingsParser;

class EmbeddingsParserLoaderTest {

    private static final String LOG_MIN_DURATION_FETCH_KEY = "org.nuxeo.OpenSearch.core.log_min_duration_fetch_ms";

    private static volatile boolean openSearchServiceImplInitialized;

    @Test
    void loadShouldReturnDefaultParserWhenServiceIsNull() {
        EmbeddingsParser parser = EmbeddingsParserLoader.load(null);

        assertThat(parser).isInstanceOf(ParquetEmbeddingsParser.class);
    }

    @Test
    void loadShouldReturnDefaultParserWhenNoContributionIsRegistered() {
        OpenSearchServiceImpl service = serviceWith(Map.of());

        EmbeddingsParser parser = EmbeddingsParserLoader.load(service);

        assertThat(parser).isInstanceOf(ParquetEmbeddingsParser.class);
    }

    @Test
    void loadShouldReturnContributedParserWhenSingleContributionIsRegistered() {
        ContributedEmbeddingsParser expected = new ContributedEmbeddingsParser();
        OpenSearchServiceImpl service = serviceWith(
                Map.of("test", descriptor(ContributedEmbeddingsParser.class, expected)));

        EmbeddingsParser parserFromService = EmbeddingsParserLoader.load(service);

        assertThat(parserFromService).isSameAs(expected);
    }

    @Test
    void loadShouldThrowWhenMultipleContributionsAreRegistered() {
        OpenSearchServiceImpl service = serviceWith(Map.of(
                "first", descriptor(ContributedEmbeddingsParser.class, new ContributedEmbeddingsParser()),
                "second", descriptor(AnotherContributedEmbeddingsParser.class, new AnotherContributedEmbeddingsParser())));

        assertThatThrownBy(() -> EmbeddingsParserLoader.load(service)).isInstanceOf(RepositoryException.class)
                                                                   .hasMessageContaining("Multiple EmbeddingsParser contributions found")
                                                                   .hasMessageContaining(ContributedEmbeddingsParser.class.getName())
                                                                   .hasMessageContaining(AnotherContributedEmbeddingsParser.class.getName())
                                                                   .hasMessageContaining("Exactly one parser is supported at this stage");
    }

    @Test
    void loadShouldReturnDefaultParserWhenFrameworkServiceIsMissing() {
        try (MockedStatic<Framework> framework = Mockito.mockStatic(Framework.class)) {
            framework.when(() -> Framework.getService(OpenSearchService.class)).thenReturn(null);

            EmbeddingsParser parser = EmbeddingsParserLoader.load();

            assertThat(parser).isInstanceOf(ParquetEmbeddingsParser.class);
        }
    }

    private static OpenSearchServiceImpl serviceWith(Map<String, EmbeddingsParserDescriptor> descriptors) {
        ensureOpenSearchServiceImplInitialized();
        OpenSearchServiceImpl service = Mockito.mock(OpenSearchServiceImpl.class);
        when(service.getEmbeddingsParserDescriptors()).thenReturn(descriptors);
        return service;
    }

    private static EmbeddingsParserDescriptor descriptor(Class<? extends EmbeddingsParser> parserClass,
            EmbeddingsParser parserInstance) {
        EmbeddingsParserDescriptor descriptor = Mockito.mock(EmbeddingsParserDescriptor.class);
        Mockito.doReturn(parserClass).when(descriptor).getParserClass();
        when(descriptor.newInstance()).thenReturn(parserInstance);
        return descriptor;
    }

    private static synchronized void ensureOpenSearchServiceImplInitialized() {
        if (openSearchServiceImplInitialized) {
            return;
        }
        try (MockedStatic<Framework> framework = Mockito.mockStatic(Framework.class)) {
            framework.when(() -> Framework.getProperty(LOG_MIN_DURATION_FETCH_KEY, "200")).thenReturn("200");
            Class.forName("org.nuxeo.opensearch.OpenSearchServiceImpl", true,
                    EmbeddingsParserLoaderTest.class.getClassLoader());
            openSearchServiceImplInitialized = true;
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Unable to initialize OpenSearchServiceImpl for unit tests", e);
        }
    }

    public static class ContributedEmbeddingsParser implements EmbeddingsParser {

        @Override
        public Collection<EmbeddingModel> parse(Blob blob, String repositoryName, String parentDocId,
                String embeddingsType) {
            return List.of();
        }
    }

    public static class AnotherContributedEmbeddingsParser implements EmbeddingsParser {

        @Override
        public Collection<EmbeddingModel> parse(Blob blob, String repositoryName, String parentDocId,
                String embeddingsType) {
            return List.of();
        }
    }
}
