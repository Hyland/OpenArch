/*
 * (C) Copyright 2025-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.opensearch.config;

import com.hyland.openarch.common.xmap.annotation.XNode;
import com.hyland.openarch.common.xmap.annotation.XObject;
import com.hyland.openarch.common.xmap.registry.XRegistry;
import com.hyland.openarch.common.xmap.registry.XRegistryId;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.opensearch.io.EmbeddingsParser;

import java.lang.reflect.InvocationTargetException;

/**
 * Descriptor to contribute an implementation of {@link EmbeddingsParser}.
 * <p>
 * Example contribution:
 * <pre>
 * {@code
 * <embeddingsParser class="com.example.ParquetEmbeddingsParser" />
 * }
 * </pre>
 * Only one active contribution is expected. The contribution defined in the test classpath
 * overrides the one defined in the main module. The parser defined in the test classpath
 * is always prioritized and used by the indexer, replacing the default one in the OpenSearch service.
 * If no contribution is found, the indexer will not parse embeddings.
 * @since 2025.0
 */
@XObject("embeddingsParser")
@XRegistry
public class EmbeddingsParserDescriptor {

    /** Fully qualified class implementing {@link EmbeddingsParser}. */
    @XNode("@class")
    @XRegistryId
    protected Class<? extends EmbeddingsParser> parserClass;

    public Class<? extends EmbeddingsParser> getParserClass() {
        return parserClass;
    }

    /** Instantiate the parser implementation. */
    public EmbeddingsParser newInstance() {
        try {
            return parserClass.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | IllegalArgumentException |
                 InvocationTargetException e) {
            throw new NuxeoException("Failed to instantiate EmbeddingsParser: " + parserClass.getName(), e);
        }
    }

    @Override
    public String toString() {
        return "EmbeddingsParserDescriptor{" + "parserClass=" + parserClass + '}';
    }
}
