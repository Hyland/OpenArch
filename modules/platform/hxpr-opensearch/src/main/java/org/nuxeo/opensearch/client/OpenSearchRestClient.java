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

import static java.util.stream.Collectors.toMap;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import io.opencensus.common.Scope;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.Span;
import io.opencensus.trace.Tracing;
import jakarta.json.spi.JsonProvider;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.ConcurrentUpdateException;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.opensearch.api.OpenSearchIndexStoreStats;
import org.nuxeo.opensearch.io.DocumentDTO;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.jackson3.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.HealthStatus;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryResponse;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.ScrollRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.generic.Body;
import org.opensearch.client.opensearch.generic.Request;
import org.opensearch.client.opensearch.generic.Requests;
import org.opensearch.client.opensearch.generic.Response;
import org.opensearch.client.opensearch.indices.stats.IndicesStatsMetric;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * @since 9.3
 */
public class OpenSearchRestClient implements org.nuxeo.opensearch.api.OpenSearchClient {
    // TODO: add security sanitizer to make sure all parameters used to build requests are clean
    private static final Logger log = LogManager.getLogger(OpenSearchRestClient.class);

    // @since 11.5
    public static final String LONG_TIMEOUT = "120s";

    protected final JsonProvider jsonProvider;

    protected final JsonpMapper jsonpMapper;

    protected final OpenSearchTransport transport;

    protected OpenSearchClient client;

    public OpenSearchRestClient(JsonProvider jsonProvider, JsonpMapper jsonpMapper, OpenSearchTransport transport) {
        this.jsonProvider = jsonProvider;
        this.jsonpMapper = jsonpMapper;
        this.transport = transport;

        open();
    }

    @Override
    public JsonProvider getJsonProvider() {
        return jsonProvider;
    }

    @Override
    public JsonpMapper getJsonpMapper() {
        return jsonpMapper;
    }

    @Override
    public ObjectMapper getObjectMapper() {
        // Reuse the transport's configured mapper (FAIL_ON_TRAILING_TOKENS / date handling set up in
        // OpenSearchRestClientFactory) rather than a bare mapper, so response parsing stays consistent.
        return ((JacksonJsonpMapper) jsonpMapper).objectMapper();
    }

    @Override
    public boolean waitForYellowStatus(String[] indexNames, int timeoutSecond) {
        Request request = Requests.builder() //
                                  .method("GET")
                                  .endpoint("/_cluster/health/%s?wait_for_status=yellow&timeout=%ds".formatted(
                                          getIndexesAsString(indexNames), timeoutSecond))
                                  .build();
        try (Response response = performRequestWithTracing(request)) {
            // unfortunately JacksonJsonProvider.createReader is unimplemented, so use Jackson directly
            JsonNode jsonNode = getObjectMapper().readTree(response.getBody().get().body());
            String status = jsonNode.get("status").asText();
            HealthStatus healthStatus = HealthStatus._DESERIALIZER.parse(status);
            switch (healthStatus) {
                case Green:
                    log.info("OpenSearch Cluster ready: {}", response.getReason());
                    return true;
                case Yellow:
                    log.warn("OpenSearch Cluster ready but not GREEN: {}", response.getReason());
                    return true;
                default:
                    String error = "OpenSearch Cluster health status: " + healthStatus + ", not Yellow after "
                            + timeoutSecond + " give up: " + response.getReason();
                    throw new IllegalArgumentException(error);
            }
        } catch (IOException | OpenSearchException e) {
            throw new NuxeoException(e);
        }
    }

    protected String getIndexesAsString(String[] indexNames) {
        return indexNames == null ? "" : String.join(",", indexNames);
    }

    @Override
    public HealthStatus getHealthStatus(String[] indexNames) {
        Request request = Requests.builder() //
                                  .method("GET")
                                  .endpoint("/_cluster/health/%s".formatted(getIndexesAsString(indexNames)))
                                  .build();
        try (Response response = performRequestWithTracing(request)) {
            // unfortunately JacksonJsonProvider.createReader is unimplemented, so use Jackson directly
            JsonNode jsonNode = getObjectMapper().readTree(response.getBody().get().body());
            String status = jsonNode.get("status").asText();
            return HealthStatus._DESERIALIZER.parse(status);
        } catch (IOException | OpenSearchException e) {
            throw new NuxeoException(e);
        }
    }

    @Override
    public void refresh(String indexName) {
        Request request = Requests.builder() //
                                  .method("POST")
                                  .endpoint("/%s/_refresh".formatted(indexName))
                                  .build();
        try (Response response = performRequestWithTracing(request)) {
        } catch (IOException | OpenSearchException e) {
            throw new NuxeoException(e);
        }
    }

    @Override
    public void flush(String indexName) {
        Request request = Requests.builder() //
                                  .method("POST")
                                  .endpoint("/%s/_flush?wait_if_ongoing=true".formatted(indexName))
                                  .build();
        try (Response response = performRequestWithTracing(request)) {
        } catch (IOException | OpenSearchException e) {
            throw new NuxeoException(e);
        }
    }

    @Override
    public void optimize(String indexName) {
        Request request = Requests.builder() //
                                  .method("POST")
                                  .endpoint("/%s/_forcemerge?max_num_segments=1".formatted(indexName))
                                  .build();
        try (Response response = performRequestWithTracing(request)) {
        } catch (IOException | OpenSearchException e) {
            throw new NuxeoException(e);
        }
    }

    @Override
    public boolean indexExists(String indexName) {
        Request request = Requests.builder() //
                                  .method("HEAD")
                                  .endpoint("/%s".formatted(indexName))
                                  .build();
        try (Response response = performRequestWithTracing(request)) {
            switch (response.getStatus()) {
                case HttpStatus.SC_OK:
                    return true;
                case HttpStatus.SC_NOT_FOUND:
                    return false;
                default:
                    throw new IllegalArgumentException(
                            String.format("Checking index %s returns: %s", indexName, response.getReason()));
            }
        } catch (IOException | OpenSearchException e) {
            throw new NuxeoException(e);
        }
    }

    @Override
    public boolean mappingExists(String indexName, String type) {
        // HEAD is not supported anymore since Open 7.x
        Request request = Requests.builder() //
                                  .method("GET")
                                  .endpoint("/%s/_mapping".formatted(indexName))
                                  .build();
        try (Response response = performRequestWithTracing(request)) {
            switch (response.getStatus()) {
                case HttpStatus.SC_OK:
                    return true;
                case HttpStatus.SC_NOT_FOUND:
                    return false;
                default:
                    throw new IllegalArgumentException(
                            String.format("Checking mapping %s returns: %s", indexName, response.getReason()));
            }
        } catch (IOException | OpenSearchException e) {
            throw new NuxeoException(e);
        }
    }

    @Override
    public void deleteIndex(String indexName, int timeoutSecond) {
        Request request = Requests.builder() //
                                  .method("DELETE")
                                  .endpoint("/%s?cluster_manager_timeout=%ds&timeout=%ds".formatted(indexName, timeoutSecond,
                                          timeoutSecond))
                                  .build();
        try (Response response = performRequestWithTracing(request)) {
            if (response.getStatus() != HttpStatus.SC_OK) {
                throw new IllegalArgumentException(String.format("Deleting %s returns: %s", indexName, response.getReason()));
            }
        } catch (IOException | OpenSearchException e) {
            if (e.getMessage() != null && e.getMessage().contains("illegal_argument_exception")) {
                // when trying to delete an alias, throws the same exception than the transport client
                throw new IllegalArgumentException(e);
            }
            throw new NuxeoException(e);
        }
    }

    @Override
    public void createIndex(String indexName, String jsonSettings) {
        Request request = Requests.builder() //
                                  .method("PUT")
                                  .endpoint("/%s?cluster_manager_timeout=%s&timeout=%s".formatted(indexName, LONG_TIMEOUT,
                                          LONG_TIMEOUT))
                                  .json("{\"settings\": " + jsonSettings + "}")
                                  .build();
        try (Response response = performRequestWithTracing(request)) {
            if (response.getStatus() != HttpStatus.SC_OK) {
                throw new NuxeoException("Fail to create index: " + indexName + " :" + response.getBody().get().bodyAsString());
            }
        } catch (IOException | OpenSearchException e) {
            throw new NuxeoException(e);
        }
    }

    @Override
    public void updateIndexDynamicSettings(String indexName, String jsonSettings) {
        Request request = Requests.builder() //
                                  .method("PUT")
                                  .endpoint("/%s/_settings".formatted(indexName))
                                  .json("{\"settings\": " + jsonSettings + "}")
                                  .build();
        try (Response response = performRequestWithTracing(request)) {
            if (response.getStatus() != HttpStatus.SC_OK) {
                throw new NuxeoException("Fail to update index settings: " + indexName + " :" + response.getBody().get().bodyAsString()); // NOSONAR
            }
        } catch (IOException | OpenSearchException e) {
            throw new NuxeoException(e);
        }
    }

    @Override
    public void createMapping(String indexName, String type, String jsonMapping) {
        Request request = Requests.builder() //
                                  .method("PUT")
                                  .endpoint("/%s/_mapping".formatted(indexName))
                                  .json(jsonMapping)
                                  .build();
        try (Response response = performRequestWithTracing(request)) {
            if (response.getStatus() != HttpStatus.SC_OK) {
                throw new NuxeoException(String.format("Fail to create mapping on %s: %s", indexName, response.getBody().get().bodyAsString()));
            }
        } catch (IOException | OpenSearchException e) {
            throw new NuxeoException(e);
        }
    }

    protected Response performRequest(Request request) {
        try {
            return client.generic().execute(request);
        } catch (IOException | OpenSearchException e) {
            throw new NuxeoException(e);
        }
    }

    /**
     * Performs an Open request using the low level client, exposed since 11.5 for internal use only.
     */
    public Response performRequestWithTracing(Request request) {
        try (Scope ignored = getScopedSpan("Open" + request.getEndpoint(), request.toString())) {
            return performRequest(request);
        }
    }

    @Override
    public boolean aliasExists(String aliasName) {
        Request request = Requests.builder() //
                                  .method("HEAD")
                                  .endpoint("/_alias/%s".formatted(aliasName))
                                  .build();
        try (Response response = performRequestWithTracing(request)) {
            switch (response.getStatus()) {
                case HttpStatus.SC_OK:
                    return true;
                case HttpStatus.SC_NOT_FOUND:
                    return false;
                default:
                    throw new IllegalArgumentException(
                            String.format("Checking alias %s returns: %s", aliasName, response.getReason()));
            }
        } catch (IOException | OpenSearchException e) {
            throw new NuxeoException(e);
        }
    }

    @Override
    public String getFirstIndexForAlias(String aliasName) {
        if (!aliasExists(aliasName)) {
            return null;
        }
        Request request = Requests.builder() //
                                  .method("GET")
                                  .endpoint("/_alias/%s".formatted(aliasName))
                                  .build();
        try (Response response = performRequestWithTracing(request)) {
            // unfortunately JacksonJsonProvider.createReader is unimplemented, so use Jackson directly
            JsonNode jsonNode = getObjectMapper().readTree(response.getBody().get().body());
            if (jsonNode.size() != 1) {
                throw new NuxeoException(String.format(
                        "Expecting alias that point to a single index, alias: %s, got: %s", aliasName, jsonNode.toPrettyString()));
            }
            return jsonNode.propertyNames().iterator().next();
        } catch (IOException | OpenSearchException e) {
            throw new NuxeoException(e);
        }
    }

    @Override
    public void updateAlias(String aliasName, String indexName) {
        // TODO do this in a single call to make it atomically
        if (aliasExists(aliasName)) {
            deleteAlias(aliasName);
        }
        if (indexExists(aliasName)) {
            throw new NuxeoException("Can create an alias because an index with the same name exists: " + aliasName);
        }
        createAlias(aliasName, indexName);
    }

    protected void deleteAlias(String aliasName) {
        Request request = Requests.builder() //
                                  .method("DELETE")
                                  .endpoint("/_all/_alias/%s".formatted(aliasName))
                                  .build();
        try (Response response = performRequestWithTracing(request)) {
            if (response.getStatus() != HttpStatus.SC_OK) {
                throw new IllegalArgumentException(
                        String.format("Deleting %s alias: %s", aliasName, response.getReason()));
            }
        } catch (IOException | OpenSearchException e) {
            throw new NuxeoException(e);
        }
    }

    protected void createAlias(String aliasName, String indexName) {
        Request request = Requests.builder() //
                                  .method("PUT")
                                  .endpoint("/%s/_alias/%s".formatted(indexName, aliasName))
                                  .build();
        try (Response response = performRequestWithTracing(request)) {
            if (response.getStatus() != HttpStatus.SC_OK) {
                throw new NuxeoException("Fail to create alias: " + indexName + " :" + response.getBody().get().bodyAsString());
            }
        } catch (IOException | OpenSearchException e) {
            throw new NuxeoException(e);
        }
    }

    @Override
    public BulkResponse bulk(BulkRequest request) {
        try (Scope ignored = getScopedSpan("open/_bulk", "actions: " + request.operations().size())) {
            if (request.timeout() == null) {
                // use a longer timeout than the default one
                request = request.toBuilder().timeout(tb -> tb.time(LONG_TIMEOUT)).build();
            }
            return client.bulk(request);
        } catch (IOException | OpenSearchException e) {
            throw new NuxeoException(e);
        }
    }

    @Override
    public void delete(String indexName, String docId) {
        try {
            client.delete(b -> b.index(indexName) //
                                .id(docId)
                                .timeout(tb -> tb.time(LONG_TIMEOUT)));
        } catch (IOException | OpenSearchException e) {
            throw new NuxeoException(e);
        }
    }

    @Override
    public DeleteByQueryResponse deleteByQuery(DeleteByQueryRequest request) {
        try {
            if (request.timeout() == null) {
                // use a longer timeout than the default one
                request = request.toBuilder().timeout(tb -> tb.time(LONG_TIMEOUT)).build();
            }
            return client.deleteByQuery(request);
        } catch (IOException | OpenSearchException e) {
            throw new NuxeoException(e);
        }
    }

    @Override
    public SearchResponse<DocumentDTO> search(SearchRequest request) {
        try (Scope ignored = getScopedSpan("open/_search", request.toJsonString())) {
            log.debug("OpenSearch search request (raw): {}", request::toJsonString);
            return client.search(request, DocumentDTO.class);
        } catch (OpenSearchException e) {
            String message;
            try {
                message = e.error().rootCause().get(0).reason();
            } catch (NullPointerException | IndexOutOfBoundsException ignored) {
                message = null;
            }
            throw new NuxeoException("Error performing OpenSearch query: " + message, e, HttpStatus.SC_BAD_REQUEST);
        } catch (IOException | JacksonException e) {
            throw new NuxeoException(e);
        }
    }

    @Override
    public SearchResponse<DocumentDTO> searchScroll(ScrollRequest request) {
        try (Scope ignored = getScopedSpan("open/_scroll", request.toString())) {
            log.debug("OpenSearch search scroll request (raw): {}", request::toJsonString);
            return client.scroll(request, DocumentDTO.class);
        } catch (IOException | OpenSearchException e) {
            throw new NuxeoException(e);
        }
    }

    @Override
    public GetResponse<DocumentDTO> get(GetRequest request) {
        try (Scope ignored = getScopedSpan("open/_get", request.toString())) {
            return client.get(request, DocumentDTO.class);
        } catch (IOException | OpenSearchException e) {
            throw new NuxeoException(e);
        }
    }

    @Override
    public IndexResponse index(IndexRequest<? extends JsonpSerializable> request) {
        try (Scope ignored = getScopedSpan("open/_index", request.toJsonString())) {
            if (request.timeout() == null) {
                // use a longer timeout than the default one
                request = request.toBuilder().timeout(tb -> tb.time(LONG_TIMEOUT)).build();
            }
            return client.index(request);
        } catch (ResponseException e) {
            if (e.status() == HttpStatus.SC_CONFLICT) {
                throw new ConcurrentUpdateException(e);
            }
            throw new NuxeoException(e);
        } catch (IOException | OpenSearchException e) {
            String message = e.getMessage();
            if (message != null && message.contains("Preview of field")) {
                // Remove PII from the preview section while keeping the rest of the error
                String sanitizedMessage = message.replaceAll("Preview of field's value:.*", "OpenSearch indexing operation failed. Preview of field's value: [REDACTED]");
                throw new NuxeoException(sanitizedMessage, e);
            }
            throw new NuxeoException(message, e);
        }
    }

    protected Scope getScopedSpan(String name, String request) {
        Scope scope = Tracing.getTracer().spanBuilder(name).setSpanKind(Span.Kind.CLIENT).startScopedSpan();
        Map<String, AttributeValue> map = new HashMap<>();
        map.put("thread", AttributeValue.stringAttributeValue(Thread.currentThread().getName()));
        map.put("request", AttributeValue.stringAttributeValue(request));
        Tracing.getTracer().getCurrentSpan().putAttributes(map);
        return scope;
    }

    @Override
    public void clearScroll(String scrollId) {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Clearing scroll id: {}", scrollId);
            }
            client.clearScroll(b -> b.scrollId(scrollId));
        } catch (OpenSearchException e) {
            if (e.status() == HttpStatus.SC_NOT_FOUND) {
                if (log.isDebugEnabled()) {
                    log.debug("Scroll id not found, it has certainly been already closed: {}", scrollId);
                }
            }
            throw new NuxeoException(e);
        } catch (IOException | JacksonException e) {
            throw new NuxeoException(e);
        }
    }

    @Override
    public OpenSearchIndexStoreStats getIndexStoreStats(Collection<String> indexNames) {
        String endpoint = "/%s/_stats/store".formatted(String.join(",", indexNames));
        Request request = Requests.builder() //
                                  .method("GET")
                                  .endpoint(endpoint)
                                  .build();
        try (Response response = performRequestWithTracing(request)) {
            JsonNode jsonNode = getObjectMapper().readTree(response.getBody().get().body());
            long totalSize = getTotalStoreSize(jsonNode.get("_all"));
            Map<String, Long> indexSizes = jsonNode.get("indices")
                                                   .properties()
                                                   .stream()
                                                   .collect(toMap( //
                                                           en -> en.getKey(), //
                                                           en -> getTotalStoreSize(en.getValue())));
            return new OpenSearchIndexStoreStats(totalSize, indexSizes);
        } catch (IOException | OpenSearchException e) {
            throw new NuxeoException(e);
        }
    }

    @Override
    public long getOpenScrollContexts() {
        try {
            var searchStats = client.indices().stats(r -> r.metric(IndicesStatsMetric.Search)).all().total().search();
            return searchStats != null ? searchStats.scrollCurrent() : 0L;
        } catch (IOException | RuntimeException e) {
            log.debug("Failed to retrieve open scroll context count", e);
            return 0L;
        }
    }

    @Override
    public void ensurePipelineWithConfig(String pipelineId, JsonNode pipelineConfig) {
        log.debug("Check if pipeline with id exists: {}", pipelineId);
        Request getPipelineRequest = Requests.builder()
                                             .endpoint("/_search/pipeline/" + pipelineId)
                                             .method("GET")
                                             .build();

        try (Response getPipelineResponse = performRequestWithTracing(getPipelineRequest)) {
            int status = getPipelineResponse.getStatus();
            log.debug("Request for {} received response: {}", pipelineId, status);
            if (status == HttpStatus.SC_OK) {
                log.debug("Pipeline {} already exists", pipelineId);
                return;
            }
            if (status != HttpStatus.SC_NOT_FOUND) {
                // Any other status (401/403/500/...) is unexpected: do not assume the pipeline exists,
                // otherwise a real error would be silently swallowed and the pipeline left missing.
                throw new NuxeoException("Failed to check pipeline " + pipelineId + ". Received status code " + status
                        + " with message: " + bodyOf(getPipelineResponse));
            }

            String pipelineJson = getObjectMapper().writeValueAsString(pipelineConfig);
            Request putPipelineRequest = Requests.builder()
                                                 .endpoint("/_search/pipeline/" + pipelineId)
                                                 .method("PUT")
                                                 .json(pipelineJson)
                                                 .build();

            log.info("Creating OpenSearch pipeline: {}", pipelineId);
            try (Response putPipelineResponse = performRequestWithTracing(putPipelineRequest)) {
                log.debug("When creating pipeline {} received status code {}", pipelineId,
                        putPipelineResponse.getStatus());

                if (putPipelineResponse.getStatus() != HttpStatus.SC_OK) {
                    throw new NuxeoException("Failed to create pipeline " + pipelineId + ". Received status code "
                            + putPipelineResponse.getStatus() + " with message: " + bodyOf(putPipelineResponse));
                }
                log.info("Successfully created OpenSearch pipeline: {}", pipelineId);
            }
        } catch (IOException | JacksonException e) {
            throw new NuxeoException(e);
        }
    }

    private static String bodyOf(Response response) {
        return response.getBody().map(Body::bodyAsString).orElse("[No body received]");
    }

    private long getTotalStoreSize(JsonNode jsonNode) {
        return jsonNode.get("total").get("store").get("size_in_bytes").asLong();
    }

    public void open() {
        client = new OpenSearchClient(transport);
    }

    @Override
    public void close() {
        if (client != null) {
            try {
                client._transport().close();
            } catch (IOException | JacksonException e) {
                log.warn("Fail to close the OpenSearch transport: {}", e.getMessage(), e);
            }
            client = null;
        }
    }

}
