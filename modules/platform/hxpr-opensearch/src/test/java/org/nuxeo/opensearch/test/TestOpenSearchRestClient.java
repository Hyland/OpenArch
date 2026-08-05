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
package org.nuxeo.opensearch.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.ConcurrentUpdateException;
import org.nuxeo.opensearch.api.OpenSearchClient;
import org.nuxeo.opensearch.client.OpenSearchRestClient;
import org.nuxeo.opensearch.client.OpenSearchRestClientFactory;
import org.nuxeo.opensearch.io.DocumentDTO;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesExtension;
import org.nuxeo.runtime.test.runner.RuntimeFeature;
import org.apache.http.HttpStatus;
import org.opensearch.client.opensearch._types.VersionType;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.generic.Request;
import org.opensearch.client.opensearch.generic.Requests;
import org.opensearch.client.opensearch.generic.Response;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Test the ESClient based on Rest Client protocol
 */
@ExtendWith(FeaturesExtension.class)
@Features(RuntimeFeature.class)
public class TestOpenSearchRestClient {

    protected OpenSearchClient client;

    protected OpenSearchRestClientFactory factory = new OpenSearchRestClientFactory();

    @AfterEach
    public void closeClient() throws Exception {
        if (client != null) {
            client.close();
        }
        client = null;
    }

    @Test
    public void testIndexOptimisticConcurrency() {
        client = factory.create();
        if (client.indexExists("foo-index")) {
            client.deleteIndex("foo-index", 1);
        }
        client.createIndex("foo-index", "{\"index\" : {\"number_of_shards\" : 1, \"number_of_replicas\" : 0}}");
        // when using external version, indexing command in disorder should be rejected
        DocumentDTO v1 = new DocumentDTO(Map.of("foo", "v1"));
        IndexRequest<DocumentDTO> request1 = new IndexRequest.Builder<DocumentDTO>().index("foo-index")
                                                                                    .id("123")
                                                                                    .versionType(VersionType.External)
                                                                                    .version(100L)
                                                                                    .document(v1)
                                                                                    .build();
        DocumentDTO v2 = new DocumentDTO(Map.of("foo", "v2"));
        IndexRequest<DocumentDTO> request2 = new IndexRequest.Builder<DocumentDTO>().index("foo-index")
                                                                                    .id("123")
                                                                                    .versionType(VersionType.External)
                                                                                    .version(200L)
                                                                                    .document(v2)
                                                                                    .build();

        // index version 2 of the document
        IndexResponse response = client.index(request2);
        assertEquals(request2.version(), (Long) response.version());

        try {
            // now index an older version of the document
            client.index(request1);
            fail("index command in disorder should raise exception");
        } catch (ConcurrentUpdateException e) {
            // expected
        }

        // confirms that the version 2 is the indexed version
        GetRequest req = GetRequest.of(b -> b.index("foo-index").id("123"));
        GetResponse<DocumentDTO> ret = client.get(req);
        assertEquals(request2.version(), ret.version());
    }

    @Test
    public void testEnsurePipelineCreatesPipeline() throws IOException {
        client = factory.create();
        String pipelineId = "test-rrf-pipeline";
        deletePipelineQuietly(pipelineId);

        client.ensurePipelineWithConfig(pipelineId, rrfPipelineConfig());

        try (Response response = getPipeline(pipelineId)) {
            assertEquals(HttpStatus.SC_OK, response.getStatus());
            JsonNode body = new ObjectMapper().readTree(response.getBody().get().body());
            assertTrue(body.has(pipelineId), "Response should contain the pipeline id");
            JsonNode processors = body.get(pipelineId).get("phase_results_processors");
            assertNotNull(processors, "Pipeline should define phase_results_processors");
            assertFalse(processors.isEmpty(), "Pipeline should have at least one processor");
            assertEquals("rrf", processors.get(0)
                                          .get("score-ranker-processor")
                                          .path("combination")
                                          .path("technique")
                                          .asText());
        } finally {
            deletePipelineQuietly(pipelineId);
        }
    }

    @Test
    public void testEnsurePipelineIsIdempotent() throws IOException {
        client = factory.create();
        String pipelineId = "test-rrf-pipeline-idempotent";
        deletePipelineQuietly(pipelineId);

        try {
            client.ensurePipelineWithConfig(pipelineId, rrfPipelineConfig());
            // A second call must not fail when the pipeline already exists.
            client.ensurePipelineWithConfig(pipelineId, rrfPipelineConfig());

            try (Response response = getPipeline(pipelineId)) {
                assertEquals(HttpStatus.SC_OK, response.getStatus());
            }
        } finally {
            deletePipelineQuietly(pipelineId);
        }
    }

    private static JsonNode rrfPipelineConfig() {
        return new ObjectMapper().valueToTree(Map.of("phase_results_processors",
                java.util.List.of(Map.of("score-ranker-processor", Map.of("combination", Map.of("technique", "rrf"))))));
    }

    private Response getPipeline(String pipelineId) {
        return performRequest(Requests.builder().endpoint("/_search/pipeline/" + pipelineId).method("GET").build());
    }

    private void deletePipelineQuietly(String pipelineId) {
        Request request = Requests.builder().endpoint("/_search/pipeline/" + pipelineId).method("DELETE").build();
        try (Response response = performRequest(request)) {
            response.getStatus(); // best-effort cleanup; pipeline may not exist
        } catch (Exception e) {
            // ignore: cleanup is best-effort
        }
    }

    private Response performRequest(Request request) {
        // performRequestWithTracing is public on OpenSearchRestClient (exposed for internal use).
        return ((OpenSearchRestClient) client).performRequestWithTracing(request);
    }

}
