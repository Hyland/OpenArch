/*
 * (C) Copyright 2022-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.vocabulary;

import static org.nuxeo.ecm.core.api.VocabularyConstants.VOCABULARY_ID;
import static org.nuxeo.ecm.core.api.VocabularyConstants.VOCABULARY_KEY;
import static org.nuxeo.rest.document.DocumentManager.DEFAULT_REPOSITORY_ID;
import static org.nuxeo.rest.utils.Headers.HXCS_REPOSITORY;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON_V1;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import org.nuxeo.ecm.core.api.VocabularyConstants;
import org.nuxeo.rest.swagger.annotation.ApiResponse200;
import org.nuxeo.rest.swagger.annotation.ApiResponse201;
import org.nuxeo.rest.swagger.annotation.ApiResponse204;
import org.nuxeo.rest.swagger.annotation.ApiResponse400;
import org.nuxeo.rest.swagger.annotation.ApiResponse404;
import org.nuxeo.rest.swagger.annotation.ApiResponse409;
import org.nuxeo.rest.swagger.annotation.Authenticated;
import org.nuxeo.rest.swagger.annotation.ParameterRepoId;
import org.nuxeo.rest.vocabulary.io.VocabularyDTO;
import org.nuxeo.rest.vocabulary.io.VocabularyEntryRequestDTO;
import org.nuxeo.rest.vocabulary.io.VocabularyEntryResponseDTO;
import org.nuxeo.rest.vocabulary.io.VocabularyIdDTO;
import org.nuxeo.runtime.springboot.transaction.Transactional;
import org.springframework.core.annotation.AliasFor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = VocabularyConstants.VOCABULARY_PATH, produces = { APPLICATION_JSON_VALUE, APP_HXCS_JSON,
        APP_HXCS_JSON_V1 })
@Tag(name = "Vocabulary", description = "Repository CRUD API")
@Authenticated
@CrossOrigin
public class VocabularyAPI {

    protected static final String VOCABULARY_ID_FORMAT = "{" + VOCABULARY_ID + "}";

    protected static final String VOCABULARY_KEY_FORMAT = "{" + VOCABULARY_KEY + "}";

    private final VocabularyManager manager;

    private final VocabularyEntryValidator vocabularyEntryValidator;

    private final VocabularyValidator vocabularyValidator;

    public VocabularyAPI(VocabularyManager manager, VocabularyEntryValidator vocabularyEntryValidator,
            VocabularyValidator vocabularyValidator) {
        this.manager = manager;
        this.vocabularyEntryValidator = vocabularyEntryValidator;
        this.vocabularyValidator = vocabularyValidator;
    }

    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Parameter(description = "The vocabulary id.")
    private @interface PathVariableId {
    }

    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Parameter(description = "The vocabulary key.")
    private @interface PathVariableKey {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @ApiResponse404()
    private @interface ApiResponse404VocabularyNotFound {
        @AliasFor(annotation = ApiResponse.class, attribute = "description")
        String description() default "Vocabulary not found.";
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @ApiResponse404()
    private @interface ApiResponse404VocabularyOrEntryNotFound {
        @AliasFor(annotation = ApiResponse.class, attribute = "description")
        String description() default "Vocabulary or vocabulary entry not found.";
    }

    @GetMapping(VOCABULARY_ID_FORMAT)
    @Operation(summary = "Returns a list of all vocabulary entries", operationId = "getVocabularyEntries")
    @ApiResponse200(description = "Returns the list of all vocabulary entries or empty.")
    @ApiResponse404VocabularyNotFound
    public List<VocabularyEntryResponseDTO> getVocabularyEntries(
            @PathVariable(VOCABULARY_ID) @PathVariableId String vocabularyId,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId) {
        return manager.getVocabularyEntries(repoId, vocabularyId);
    }

    @GetMapping(VOCABULARY_ID_FORMAT + "/" + VOCABULARY_KEY_FORMAT)
    @Operation(summary = "Returns particular vocabulary entry", operationId = "getVocabularyEntry")
    @ApiResponse200(description = "Returns the vocabulary entry.")
    @ApiResponse404VocabularyOrEntryNotFound
    public VocabularyEntryResponseDTO getVocabularyEntry(
            @PathVariable(VOCABULARY_ID) @PathVariableId String vocabularyId,
            @PathVariable(VOCABULARY_KEY) @PathVariableKey String vocabularyKey,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId) {
        return manager.getVocabularyEntry(repoId, vocabularyId, vocabularyKey);
    }

    @PutMapping(path = VOCABULARY_ID_FORMAT, consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Updates and returns particular VocabularyEntry", operationId = "updateVocabularyEntry")
    @ApiResponse200(description = "Returns the updated vocabulary entry.")
    @ApiResponse400(description = "Invalid vocabulary entry key or label.")
    @ApiResponse404VocabularyOrEntryNotFound
    public VocabularyEntryResponseDTO updateVocabularyEntry(
            @PathVariable(VOCABULARY_ID) @PathVariableId String vocabularyId,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId,
            @RequestBody(required = false) VocabularyEntryRequestDTO entry) {
        vocabularyEntryValidator.validate(entry);
        return manager.updateVocabularyEntry(repoId, vocabularyId, entry);
    }

    @GetMapping
    @Operation(summary = "Returns a list of all vocabularies", operationId = "getVocabularyList")
    @ApiResponse200(description = "Returns the list of all vocabularies or empty.")
    public List<VocabularyIdDTO> getVocabularyList(
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId) {
        return manager.getVocabularyList(repoId);
    }

    @PostMapping(consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Creates a vocabulary", operationId = "createVocabulary")
    @ApiResponse201(description = "Returns the created vocabulary.")
    @ApiResponse400(description = "Invalid vocabulary id.")
    @ApiResponse409(description = "Vocabulary already exists.")
    @Transactional
    public ResponseEntity<VocabularyDTO> createVocabulary(
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId,
            @RequestBody(required = false) VocabularyDTO vocabulary) {
        vocabularyValidator.validate(vocabulary);
        return ResponseEntity.status(CREATED).body(manager.createVocabulary(repoId, vocabulary));
    }

    @PostMapping(path = VOCABULARY_ID_FORMAT, consumes = APPLICATION_JSON_VALUE)
    @ApiResponse201(description = "Returns the created vocabulary entry.")
    @Operation(summary = "Creates an entry for vocabulary", operationId = "createVocabularyEntry")
    @ApiResponse400(description = "Invalid vocabulary entry key or label.")
    @ApiResponse404VocabularyNotFound
    @ApiResponse409(description = "Entry already exists in this vocabulary.")
    @Transactional
    public ResponseEntity<VocabularyEntryResponseDTO> createVocabularyEntry(
            @PathVariable(VOCABULARY_ID) @PathVariableId String vocabularyId,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId,
            @RequestBody(required = false) VocabularyEntryRequestDTO vocabularyEntry) {
        vocabularyEntryValidator.validate(vocabularyEntry);
        return ResponseEntity.status(CREATED)
                             .body(manager.createVocabularyEntry(repoId, vocabularyId, vocabularyEntry));
    }

    @DeleteMapping(VOCABULARY_ID_FORMAT)
    @Operation(summary = "Deletes vocabulary along with its entries", operationId = "deleteVocabulary")
    @ApiResponse204(description = "Vocabulary removed successfully.")
    @ApiResponse404VocabularyNotFound
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteVocabulary(@PathVariable(VOCABULARY_ID) @PathVariableId String vocabularyId,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId) {
        manager.deleteVocabulary(repoId, vocabularyId);
    }

    @DeleteMapping(VOCABULARY_ID_FORMAT + "/" + VOCABULARY_KEY_FORMAT)
    @Operation(summary = "Deletes entry of a vocabulary", operationId = "deleteVocabularyEntry")
    @ApiResponse204(description = "Vocabulary entry removed successfully.")
    @ApiResponse404VocabularyOrEntryNotFound
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteVocabularyEntry(@PathVariable(VOCABULARY_ID) @PathVariableId String vocabularyId,
            @PathVariable(VOCABULARY_KEY) @PathVariableKey String vocabularyKey,
            @RequestHeader(name = HXCS_REPOSITORY, defaultValue = DEFAULT_REPOSITORY_ID) @ParameterRepoId String repoId) {
        manager.deleteVocabularyEntry(repoId, vocabularyId, vocabularyKey);
    }
}
