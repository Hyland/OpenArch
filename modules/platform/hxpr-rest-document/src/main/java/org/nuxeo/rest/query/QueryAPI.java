/*
 * (C) Copyright 2021-2025 Hyland (http://www.hyland.com/) and others.
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
 *
 * Contributors:
 *     Anahide Tchertchian
 */
package org.nuxeo.rest.query;

import static java.lang.Boolean.FALSE;
import static org.nuxeo.rest.document.DocumentManager.DEFAULT_REPOSITORY_ID;
import static org.nuxeo.rest.utils.Headers.HXCS_REPOSITORY;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON_V1;
import static org.springframework.http.MediaType.ALL_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.rest.exception.HxprErrorResponse;
import org.nuxeo.rest.query.io.DocumentAdvancedQueryDTO;
import org.nuxeo.rest.query.io.DocumentNamedQueryDTO;
import org.nuxeo.rest.query.io.DocumentQueryDTO;
import org.nuxeo.rest.query.io.DocumentQueryResultDTO;
import org.nuxeo.rest.query.io.EmbeddingQueryDTO;
import org.nuxeo.rest.query.io.EmbeddingQueryResultDTO;
import org.nuxeo.rest.query.io.TermsAggregationQueryResultDTO;
import org.nuxeo.rest.query.io.TermsAggregationsQueryDTO;
import org.nuxeo.rest.query.spec.NamedQueryDefinition;
import org.nuxeo.rest.query.spec.NamedQueryRegistry;
import org.nuxeo.rest.swagger.annotation.ApiResponse200;
import org.nuxeo.rest.swagger.annotation.ApiResponse400;
import org.nuxeo.rest.swagger.annotation.ApiResponse404;
import org.nuxeo.rest.swagger.annotation.Authenticated;
import org.nuxeo.rest.swagger.annotation.ParameterRepoId;
import org.nuxeo.runtime.springboot.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Document Query Rest API, targeting the repository or OpenSearch.
 */
@RestController
@RequestMapping(path = QueryAPI.PATH, produces = { APPLICATION_JSON_VALUE, APP_HXCS_JSON, APP_HXCS_JSON_V1 })
@Tag(name = "Query", description = "Repository Query API")
@Authenticated
public class QueryAPI {

    public static final String PATH = "/api/query"; // NOSONAR

    private final OpenSearchQueryManager osqm;

    private final RepositoryQueryManager rqm;

    private final NamedQueryRegistry namedQueryRegistry;

    public QueryAPI(OpenSearchQueryManager osqm, RepositoryQueryManager rqm, NamedQueryRegistry namedQueryRegistry) {
        this.osqm = osqm;
        this.rqm = rqm;
        this.namedQueryRegistry = namedQueryRegistry;
    }

    protected CoreSession getCoreSession(String repoId) {
        return CoreInstance.getCoreSession(repoId);
    }

    @PostMapping
    @Operation(summary = "Returns the search results.", operationId = "getDocumentsByQuery")
    @ApiResponse200(description = "Returns the search results for given query.")
    @Transactional
    public DocumentQueryResultDTO getDocumentsByPost(
            @RequestBody(required = false) DocumentQueryDTO query,
            @RequestParam(name = "sync", required = false, defaultValue = "false") boolean sync) {
        if (query == null) {
            query = new DocumentQueryDTO(null);
        }
        return getQueryManager(sync).getResults(query);
    }

    @PostMapping("/advanced")
    @Operation(summary = "Returns the advanced search results.", operationId = "getDocumentsByAdvancedQuery")
    @ApiResponse200(description = "Returns the search results for given advanced query.")
    @Transactional
    public DocumentQueryResultDTO getDocumentsByAdvancedPost(
            @RequestBody(required = false) DocumentAdvancedQueryDTO query,
            @RequestParam(name = "sync", required = false, defaultValue = "false") boolean sync) {
        if (query == null) {
            query = new DocumentAdvancedQueryDTO((String) null);
        }
        return getQueryManager(sync).getResults(query);
    }

    @GetMapping(path = "/named", consumes = ALL_VALUE)
    @Operation(summary = "Returns the named query definition names.", operationId = "getNamedQueries")
    @ApiResponse200(description = "Returns the named queries names.", content = @Content(schema = @Schema(type = "object"), //
            examples = @ExampleObject("{ \"namedQueries\": [\"query1\", \"query2\"] }")))
    public Map<String, List<String>> getNamedQueries() {
        return Map.of("namedQueries", namedQueryRegistry.getNamedQueries().keySet().stream().sorted().toList());
    }

    @GetMapping(path = "/named/{namedQuery}", consumes = ALL_VALUE)
    @Operation(summary = "Returns the named query definition.", operationId = "getNamedQuery")
    @ApiResponse200(description = "Returns the named query definition.")
    @ApiResponse404(description = "Named query not found.")
    @Transactional
    public NamedQueryDefinition getNamedQuery(
            @PathVariable("namedQuery") @Parameter(description = "The named query id.") String namedQuery) {
        NamedQueryDefinition query = namedQueryRegistry.getNamedQuery(namedQuery);
        if (query == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Named query not found.");
        }
        return query;
    }

    @PostMapping("/named")
    @Operation(summary = "Returns the search results for given named query.", operationId = "getDocumentsByNamedQuery")
    @ApiResponse200(description = "Returns the search results for given named query.")
    @ApiResponse404(description = "Named query not found.")
    @Transactional
    public DocumentQueryResultDTO getDocumentsByNamedPost(
            @RequestBody(required = false) DocumentNamedQueryDTO query,
            @RequestParam(name = "sync", required = false, defaultValue = "false") boolean sync) {
        var result = getQueryManager(sync).getResults(query);
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Named query not found.");
        }
        return result;
    }

    @GetMapping(path = "/waitForFullTextSearchIndexing", consumes = ALL_VALUE)
    @Operation(summary = "Waits for for full text search indexing.", operationId = "waitForFullTextSearchIndexing")
    @ApiResponse200(description = "Waits for full text search indexing.", content = {
            @Content(schema = @Schema(type = "object"), examples = @ExampleObject("{ \"indexing\": \"done\" }")) })
    @Transactional
    public Map<String, Object> waitForFullTextSearchIndexing(
            @RequestParam(name = "refresh", required = false, defaultValue = "true") @Parameter(description = "Refresh status.") Boolean refresh,
            @RequestParam(name = "timeout", required = false, defaultValue = "5") @Parameter(description = "The timeout (in seconds).") Integer timeout,
            @RequestParam(name = "waitForAudit", required = false, defaultValue = "false") @Parameter(description = "Wait for audit (deprecated, unused).") Boolean waitForAudit,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId) {
        boolean done = osqm.waitForIndexing(repoId, timeout == null ? 5 : timeout, !FALSE.equals(refresh));
        return Map.of("indexing", done ? "done" : "not done");
    }

    @PostMapping("/embeddings")
    @Operation(summary = "Vector search.", operationId = "getEmbeddingsByQuery")
    @ApiResponse200(description = "Returns the nearest embeddings for given query.")
    @Transactional
    public EmbeddingQueryResultDTO getEmbeddingsByPost(@RequestBody(required = false) EmbeddingQueryDTO query) {
        return osqm.getResults(query);
    }

    @PostMapping("/termsAggregation")
    @Operation(summary = "Terms aggregation query on a given property.", operationId = "queryTermsAggregation")
    @ApiResponse200(description = "Returns the top N terms aggregations matching a given search expression for the given property.", content = {
        @Content(schema = @Schema(implementation = TermsAggregationQueryResultDTO.class),
        examples = @ExampleObject(value = QueryRequestConstants.TERMS_AGGREGATION_RESPONSE_EXAMPLE)) })
    @ApiResponse400(description = "Invalid request body. Please be aware that termsAggregationProperty is required", content = {
            @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = HxprErrorResponse.class), examples = {
                    @ExampleObject(value = QueryRequestConstants.TERMS_AGGREGATION_INVALID_INPUT_EXAMPLE) }) })
    @Transactional
    public TermsAggregationQueryResultDTO queryTermsAggregation(
            @RequestBody(required = true) TermsAggregationsQueryDTO query) {
        return osqm.getResults(query.query(), query.termsAggregationProperty(), query.searchTerm(), query.limit());
    }

    private QueryManager getQueryManager(boolean sync) {
        return sync ? rqm : osqm;
    }
}
