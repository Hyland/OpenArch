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
package com.hyland.openarch.opensearch;

import java.util.Map;
import java.util.stream.Collectors;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import com.hyland.openarch.runtime.api.Framework;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hyland.openarch.opensearch.api.OpenSearchService;
import com.hyland.openarch.opensearch.config.EmbeddingsParserDescriptor;
import com.hyland.openarch.opensearch.io.EmbeddingsParser;

/**
 * Utility for loading the embeddings parser with validation that exactly one parser is registered.
 * Throws NuxeoException if multiple parsers are found; falls back to default if none.
 */
public class EmbeddingsParserLoader {
    private static final Logger log = LogManager.getLogger(EmbeddingsParserLoader.class);

    private EmbeddingsParserLoader() {
        // utility class
    }

    /**
     * Loads the parser, attempting to get the OpenSearchService via Framework.
     * The service is registered as the interface OpenSearchService, and if it's an instance
     * of OpenSearchServiceImpl, we can access its parser descriptors. Otherwise, falls back
     * to the default parser.
     */
    public static EmbeddingsParser load() {
        OpenSearchService service = Framework.getService(OpenSearchService.class);
        if (service instanceof OpenSearchServiceImpl impl) {
            return load(impl);
        }
        log.debug("OpenSearchServiceImpl not available; using default EmbeddingsParser");
        return EmbeddingsParser.newParser();
    }

    /**
     * Loads the parser using the provided service implementation.
     */
    public static EmbeddingsParser load(OpenSearchServiceImpl service) {
        if (service == null) {
            log.debug("OpenSearchServiceImpl not available; using default EmbeddingsParser");
            return EmbeddingsParser.newParser();
        }

        Map<String, EmbeddingsParserDescriptor> descriptors = service.getEmbeddingsParserDescriptors();

        if (descriptors.size() > 1) {
            // should never occur: tests should ensure only one contribution is active
            String classes = descriptors.values().stream().map(d -> d.getParserClass().getName()).collect(Collectors.joining(", "));
            throw new RepositoryException("Multiple EmbeddingsParser contributions found (" + classes + "). Exactly one parser is supported at this stage.");
        }

        if (descriptors.size() == 1) {
            // should only occur from test contributions
            EmbeddingsParserDescriptor desc = descriptors.values().iterator().next();
            EmbeddingsParser parser = newParser(desc);
            log.info("Using EmbeddingsParser implementation: {}", parser.getClass().getName());

            return parser;
        }

        return EmbeddingsParser.newParser();
    }

    private static EmbeddingsParser newParser(EmbeddingsParserDescriptor desc) {
        try {
            EmbeddingsParser parser = desc.newInstance();
            log.debug("Registered EmbeddingsParser: {}", desc.getParserClass().getName());
            return parser;
        } catch (RuntimeException e) {
            throw new RepositoryException("Failed to instantiate EmbeddingsParser: " + desc.getParserClass().getName(), e);
        }
    }
}
