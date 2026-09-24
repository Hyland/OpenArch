/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.CONFLICT;
import static jakarta.ws.rs.core.Response.Status.CREATED;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.NO_CONTENT;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.hamcrest.Matchers.is;
import static org.nuxeo.ecm.core.api.VocabularyConstants.VOCABULARY_ID;
import static org.nuxeo.ecm.core.api.VocabularyConstants.VOCABULARY_KEY;
import static org.nuxeo.ecm.core.api.VocabularyConstants.VOCABULARY_LABEL;
import static org.nuxeo.ecm.core.api.VocabularyConstants.VOCABULARY_PATH;
import static org.nuxeo.ecm.core.api.VocabularyConstants.VOCABULARY_REPOSITORY_SUFFIX;
import static org.nuxeo.ecm.core.api.local.DummyLoginFeature.TEST_ANONYMOUS_USER;
import static org.nuxeo.rest.document.test.ContentTestHelper.getJsonPath;
import static org.nuxeo.rest.utils.Headers.HXCS_REPOSITORY;
import static org.nuxeo.rest.vocabulary.exception.VocabularyValidationException.VOCABULARY_ID_BLANK_OR_NULL_MESSAGE;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.ws.rs.core.MediaType;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.VocabularyConstants;
import org.nuxeo.ecm.core.api.local.WithUser;
import org.nuxeo.rest.document.test.AbstractAPITest;
import com.hyland.openarch.runtime.mockito.RuntimeInject;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.ResultActions;

@Import({ VocabularyAPITestConfiguration.class })
@SpringBootTest(classes = { VocabularyAPI.class })
public class VocabularyAPITest extends AbstractAPITest {

    private static final String FIRST_ENTRY_PAYLOAD = """
            {
                "sysvocab_key": "myVocabularyKey",
                "sysvocab_label": "myVocabularyLabel"
            }
            """;

    private static final String SECOND_ENTRY_PAYLOAD = """
            {
                "sysvocab_key": "myVocabularyKey2",
                "sysvocab_label": "myVocabularyLabel2"
            }
            """;

    private static final String THIRD_ENTRY_PAYLOAD = """
            {
                "sysvocab_key": "myVocabularyKey3",
                "sysvocab_label": "myVocabularyLabel3"
            }
            """;

    private static final String MY_VOCABULARY_PAYLOAD = """
            {
                "sysvocab_id": "myVocabulary"
            }
            """;

    private static final String MY_VOCABULARY_ID = "myVocabulary";

    private static final String MY_VOCABULARY_LABEL = "myVocabularyLabel";

    private static final String MY_VOCABULARY_KEY = "myVocabularyKey";

    private static final String MESSAGE = "message";

    @RuntimeInject
    protected CoreSession coreSession;

    @Test
    @WithUser(TEST_ANONYMOUS_USER)
    void shouldRejectUnauthorizedPost() throws Exception {
        mvc.perform(post(VOCABULARY_PATH).header(HXCS_REPOSITORY, REPO_ID).content("{}").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldCreateVocabulary() throws Exception {
        createVocabulary().andExpect(jsonPath(VOCABULARY_ID).value(MY_VOCABULARY_ID));
    }

    @Test
    void shouldReturn409WhenPostingSameVocabulary() throws Exception {
        createVocabulary().andExpect(jsonPath(VOCABULARY_ID).value(MY_VOCABULARY_ID));

        // try to POST myVocabulary once again
        checkResponseWithPayload(CONFLICT, POST, MediaType.APPLICATION_JSON, REPO_ID, MY_VOCABULARY_PAYLOAD,
                VOCABULARY_PATH);
    }

    /**
     * All three write handlers declared the body optional and then handed it straight to a validator, which
     * dereferenced it -- so the JSON literal null answered 500 rather than 400 (HXCS-8835). None of them can do
     * anything without a body, so the body is required now and Spring rejects the request before the handler.
     */
    @Test
    void shouldReturn400WhenCreatingVocabularyWithoutBody() throws Exception {
        checkResponseWithPayload(BAD_REQUEST, POST, MediaType.APPLICATION_JSON, REPO_ID, "null", VOCABULARY_PATH)
                .andExpect(jsonPath(MESSAGE).value("Required request body is missing"));
    }

    @Test
    void shouldReturn400WhenCreatingVocabularyEntryWithoutBody() throws Exception {
        checkResponseWithPayload(BAD_REQUEST, POST, MediaType.APPLICATION_JSON, REPO_ID, "null",
                VOCABULARY_PATH + "/" + MY_VOCABULARY_ID)
                .andExpect(jsonPath(MESSAGE).value("Required request body is missing"));
    }

    @Test
    void shouldReturn400WhenUpdatingVocabularyEntryWithoutBody() throws Exception {
        checkResponseWithPayload(BAD_REQUEST, PUT, MediaType.APPLICATION_JSON, REPO_ID, "null",
                VOCABULARY_PATH + "/" + MY_VOCABULARY_ID)
                .andExpect(jsonPath(MESSAGE).value("Required request body is missing"));
    }

    @Test
    void shouldReturn400WhenCreatingVocabularyWithNullVocabularyId() throws Exception {
        final String NULL_VOCABULARY_PAYLOAD = """
            {
                "sysvocab_id": null
            }
            """;
        checkResponseWithPayload(BAD_REQUEST, POST, MediaType.APPLICATION_JSON, REPO_ID, NULL_VOCABULARY_PAYLOAD,
                VOCABULARY_PATH).andExpect(jsonPath(MESSAGE).value(VOCABULARY_ID_BLANK_OR_NULL_MESSAGE));
    }

    @Test
    void shouldReturn400WhenWhenCreatingVocabularyWithBlankId() throws Exception {
        final String NULL_VOCABULARY_PAYLOAD = """
            {
                "sysvocab_id": "   "
            }
            """;
        checkResponseWithPayload(BAD_REQUEST, POST, MediaType.APPLICATION_JSON, REPO_ID, NULL_VOCABULARY_PAYLOAD,
                VOCABULARY_PATH).andExpect(jsonPath(MESSAGE).value(VOCABULARY_ID_BLANK_OR_NULL_MESSAGE));
    }

    @Test
    void shouldCreateAndDeleteVocabularyEntry() throws Exception {
        createVocabulary().andExpect(jsonPath(VOCABULARY_ID).value(MY_VOCABULARY_ID));

        // Add Entry
        addEntryToVocabulary(MY_VOCABULARY_ID, FIRST_ENTRY_PAYLOAD).andExpectAll(
                jsonPath(VOCABULARY_KEY).value(MY_VOCABULARY_KEY), jsonPath(VOCABULARY_ID).value(MY_VOCABULARY_ID),
                jsonPath(VOCABULARY_LABEL).value(MY_VOCABULARY_LABEL));

        // check if entry was added successfully
        checkResponseWithPayload(OK, GET, REPO_ID, "", VOCABULARY_PATH + "/myVocabulary/myVocabularyKey").andExpectAll(
                jsonPath(VOCABULARY_KEY).value(MY_VOCABULARY_KEY),
                jsonPath(VOCABULARY_LABEL).value(MY_VOCABULARY_LABEL));

        // remove entry
        checkResponseWithPayload(NO_CONTENT, DELETE, REPO_ID, "", VOCABULARY_PATH + "/myVocabulary/myVocabularyKey");

        // check that entry is removed
        checkResponseWithPayload(NOT_FOUND, GET, REPO_ID, "", VOCABULARY_PATH + "/myVocabulary/myVocabularyKey");
    }

    @Test
    void shouldCreateAndDeleteVocabularyEntries() throws Exception {
        createVocabulary().andExpect(jsonPath(VOCABULARY_ID).value(MY_VOCABULARY_ID));

        addEntryToVocabulary(MY_VOCABULARY_ID, FIRST_ENTRY_PAYLOAD);

        // add 2nd entry
        addEntryToVocabulary(MY_VOCABULARY_ID, SECOND_ENTRY_PAYLOAD).andExpectAll(
                jsonPath(VOCABULARY_KEY).value("myVocabularyKey2"), jsonPath(VOCABULARY_ID).value(MY_VOCABULARY_ID),
                jsonPath(VOCABULARY_LABEL).value("myVocabularyLabel2"));

        // add 3rd entry
        addEntryToVocabulary(MY_VOCABULARY_ID, THIRD_ENTRY_PAYLOAD).andExpectAll(
                jsonPath(VOCABULARY_KEY).value("myVocabularyKey3"), jsonPath(VOCABULARY_ID).value(MY_VOCABULARY_ID),
                jsonPath(VOCABULARY_LABEL).value("myVocabularyLabel3"));

        // check entries were added
        checkResponseWithPayload(OK, GET, REPO_ID, "", VOCABULARY_PATH + "/myVocabulary/myVocabularyKey").andExpectAll(
                jsonPath(VOCABULARY_KEY).value("myVocabularyKey"),
                jsonPath(VOCABULARY_LABEL).value("myVocabularyLabel"));

        checkResponseWithPayload(OK, GET, REPO_ID, "", VOCABULARY_PATH + "/myVocabulary/myVocabularyKey2").andExpectAll(
                jsonPath(VOCABULARY_KEY).value("myVocabularyKey2"),
                jsonPath(VOCABULARY_LABEL).value("myVocabularyLabel2"));

        checkResponseWithPayload(OK, GET, REPO_ID, "", VOCABULARY_PATH + "/myVocabulary/myVocabularyKey3").andExpectAll(
                jsonPath(VOCABULARY_KEY).value("myVocabularyKey3"),
                jsonPath(VOCABULARY_LABEL).value("myVocabularyLabel3"));

        // delete entries
        checkResponseWithPayload(NO_CONTENT, DELETE, REPO_ID, "", VOCABULARY_PATH + "/myVocabulary/myVocabularyKey");
        checkResponseWithPayload(NO_CONTENT, DELETE, REPO_ID, "", VOCABULARY_PATH + "/myVocabulary/myVocabularyKey2");
        checkResponseWithPayload(NO_CONTENT, DELETE, REPO_ID, "", VOCABULARY_PATH + "/myVocabulary/myVocabularyKey3");

        // confirm that all entries are removed
        checkResponseWithPayload(NOT_FOUND, GET, REPO_ID, "", VOCABULARY_PATH + "/myVocabulary/myVocabularyKey");
        checkResponseWithPayload(NOT_FOUND, GET, REPO_ID, "", VOCABULARY_PATH + "/myVocabulary/myVocabularyKey2");
        checkResponseWithPayload(NOT_FOUND, GET, REPO_ID, "", VOCABULARY_PATH + "/myVocabulary/myVocabularyKey3");
    }

    @Test
    void testDeleteVocabularyEntryWhenTheresNone() throws Exception {
        createVocabulary();
        checkResponse(NOT_FOUND, DELETE, REPO_ID, VOCABULARY_PATH + "/" + MY_VOCABULARY_ID + "/noEntry");
    }

    @Test
    void testDeleteVocabularyEntryWhenTheresNoVocabulary() throws Exception {
        checkResponse(NOT_FOUND, DELETE, REPO_ID, VOCABULARY_PATH + "/noVocab/noEntry");
    }

    @Test
    void shouldReturn409WhenTryingToPOSTSameVocabularyEntry() throws Exception {
        createVocabulary().andExpect(jsonPath(VOCABULARY_ID).value(MY_VOCABULARY_ID));

        addEntryToVocabulary(MY_VOCABULARY_ID, FIRST_ENTRY_PAYLOAD).andExpectAll(
                jsonPath(VOCABULARY_KEY).value(MY_VOCABULARY_KEY),
                jsonPath(VOCABULARY_LABEL).value(MY_VOCABULARY_LABEL));

        checkResponseWithPayload(CONFLICT, POST, MediaType.APPLICATION_JSON, REPO_ID, FIRST_ENTRY_PAYLOAD,
                VOCABULARY_PATH + "/myVocabulary");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"    \""})
    void shouldReturn400WhenCreatingVocabularyEntryWithNullOrEmptyKey(String key) throws Exception {
        createVocabulary().andExpect(jsonPath(VOCABULARY_ID).value(MY_VOCABULARY_ID));

        final String BAD_ENTRY_PAYLOAD = """
                {
                    "sysvocab_key": %s,
                    "sysvocab_label": "myVocabularyLabel"
                }
                """.formatted(key);

        checkResponseWithPayload(BAD_REQUEST, POST, MediaType.APPLICATION_JSON, REPO_ID, BAD_ENTRY_PAYLOAD,
                VOCABULARY_PATH + "/myVocabulary").andExpect(
                    jsonPath(MESSAGE).value("Vocabulary entry key is null or blank"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"    \""}) // cannot use `NullAndEmptySource` because we need the quotes for the empty string
    void shouldReturn400WhenCreatingVocabularyEntryWithNullOrBlankLabel(String key) throws Exception {
        createVocabulary().andExpect(jsonPath(VOCABULARY_ID).value(MY_VOCABULARY_ID));

        final String BAD_ENTRY_PAYLOAD = """
                {
                    "sysvocab_key": "key",
                    "sysvocab_label": %s
                }
                """.formatted(key);
        checkResponseWithPayload(BAD_REQUEST, POST, MediaType.APPLICATION_JSON, REPO_ID, BAD_ENTRY_PAYLOAD,
                VOCABULARY_PATH + "/myVocabulary");
    }

    @Test
    void shouldReturn404WhenCreatingEntryToNonExistingVocabulary() throws Exception {
        String payload = """
                {
                    "sysvocab_key": "myVocabularyKey",
                    "sysvocab_label": "myVocabularyLabel"
                }
                """;
        checkResponseWithPayload(NOT_FOUND, POST, MediaType.APPLICATION_JSON, REPO_ID, payload,
                VOCABULARY_PATH + "/NON_EXISTING_VOCAB/");
    }

    @Test
    void testGetAllVocabularies() throws Exception {
        createVocabulary().andExpect(jsonPath(VOCABULARY_ID).value(MY_VOCABULARY_ID));

        String payload2 = """
                {
                    "sysvocab_id": "myVocabulary2"
                }
                """;
        checkResponseWithPayload(CREATED, POST, MediaType.APPLICATION_JSON, REPO_ID, payload2, VOCABULARY_PATH);
        String payload3 = """
                {
                    "sysvocab_id": "myVocabulary3"
                }
                """;
        checkResponseWithPayload(CREATED, POST, MediaType.APPLICATION_JSON, REPO_ID, payload3, VOCABULARY_PATH);

        checkResponse(OK, GET, REPO_ID, VOCABULARY_PATH).andExpect(jsonPath("$.length()", is(3)));
    }

    @Test
    void testGetAllVocabulariesWhenThereIsNone() throws Exception {
        checkResponse(OK, GET, REPO_ID, VOCABULARY_PATH).andExpect(jsonPath("$.length()", is(0)));
    }

    @Test
    void testDeleteVocabulary() throws Exception {
        createVocabulary();
        checkResponse(NO_CONTENT, DELETE, REPO_ID, VOCABULARY_PATH + "/" + MY_VOCABULARY_ID);
    }

    @Test
    void testAttemptDeleteNonexistentVocabulary() throws Exception {
        checkResponse(NOT_FOUND, DELETE, REPO_ID, VOCABULARY_PATH + "/" + UUID.randomUUID());
    }

    @Test
    void testAttemptDeleteEntryFromNonexistentVocabulary() throws Exception {
        checkResponse(NOT_FOUND, DELETE, REPO_ID, VOCABULARY_PATH + "/" + UUID.randomUUID() + "/randomEntry");
    }

    @Test
    void testGetVocabularyEntry() throws Exception {
        var result = createVocabulary();
        String vocabId = getJsonPath(result, VOCABULARY_ID);
        result = addEntryToVocabulary(vocabId, FIRST_ENTRY_PAYLOAD);
        var entryKey = getJsonPath(result, VOCABULARY_KEY);

        checkResponse(OK, GET, REPO_ID, VOCABULARY_PATH + "/" + vocabId + "/" + entryKey).andExpectAll(
                jsonPath(VOCABULARY_KEY).value(MY_VOCABULARY_KEY),
                jsonPath(VOCABULARY_LABEL).value(MY_VOCABULARY_LABEL));
    }

    @Test
    void testGetVocabularyEntryWhenNoVocabulary() throws Exception {
        checkResponse(NOT_FOUND, GET, REPO_ID, VOCABULARY_PATH + "/hehe/hihi");
    }

    @Test
    void testGetVocabularyEntryWhenNoEntry() throws Exception {
        var result = createVocabulary();
        String vocabId = getJsonPath(result, VOCABULARY_ID);
        checkResponse(NOT_FOUND, GET, REPO_ID, VOCABULARY_PATH + "/" + vocabId + "/hihi");
    }

    @Test
    void testGetVocabularyEntries() throws Exception {
        var result = createVocabulary();
        String vocabId = getJsonPath(result, VOCABULARY_ID);
        addEntryToVocabulary(vocabId, FIRST_ENTRY_PAYLOAD);
        addEntryToVocabulary(vocabId, SECOND_ENTRY_PAYLOAD);

        checkResponse(OK, GET, REPO_ID, VOCABULARY_PATH + "/" + vocabId).andExpect(
                jsonPath("$.length()", is(2)));
    }

    @Test
    void testGetVocabularyEntriesWhenNoVocabulary() throws Exception {
        checkResponse(NOT_FOUND, GET, REPO_ID, VOCABULARY_PATH + "/" + "hehehihi");
    }

    @Test
    void testGetVocabularyEntriesWhenTheresNone() throws Exception {
        var result = createVocabulary();
        String vocabId = getJsonPath(result, VOCABULARY_ID);
        checkResponse(OK, GET, REPO_ID, VOCABULARY_PATH + "/" + vocabId).andExpect(
                jsonPath("$.length()", is(0)));
    }

    @Test
    void testUpdateVocabularyEntry() throws Exception {
        // given
        var createVocabularyResult = createVocabulary();
        String vocabId = getJsonPath(createVocabularyResult, VOCABULARY_ID);
        addEntryToVocabulary(vocabId, FIRST_ENTRY_PAYLOAD);

        // when
        var updatedEntryPayload = """
                {
                    "sysvocab_key": "myVocabularyKey",
                    "sysvocab_label": "updatedVocabularyLabel"
                }
                """;
        checkResponseWithPayload(OK, PUT, MediaType.APPLICATION_JSON, REPO_ID, updatedEntryPayload,
                VOCABULARY_PATH + "/" + vocabId);

        // then
        checkResponse(OK, GET, REPO_ID, VOCABULARY_PATH + "/" + vocabId + "/myVocabularyKey").andExpectAll(
                jsonPath(VOCABULARY_KEY).value(MY_VOCABULARY_KEY),
                jsonPath(VOCABULARY_LABEL).value("updatedVocabularyLabel"));
    }

    @Test
    void shouldReturn400WhenUpdatingVocabularyEntryWithNullKey() throws Exception {
        // given
        var createVocabularyResult = createVocabulary();
        String vocabId = getJsonPath(createVocabularyResult, VOCABULARY_ID);
        addEntryToVocabulary(vocabId, FIRST_ENTRY_PAYLOAD);

        // when
        var updatedEntryPayload = """
                {
                    "sysvocab_key": null,
                    "sysvocab_label": "updatedVocabularyLabel"
                }
                """;
        checkResponseWithPayload(BAD_REQUEST, PUT, MediaType.APPLICATION_JSON, REPO_ID, updatedEntryPayload,
                VOCABULARY_PATH + "/" + vocabId).andExpect(
                jsonPath(MESSAGE).value("Vocabulary entry key is null or blank"));
    }

    @Test
    void shouldReturn400WhenUpdatingVocabularyEntryWithBlankLabel() throws Exception {
        // given
        var createVocabularyResult = createVocabulary();
        String vocabId = getJsonPath(createVocabularyResult, VOCABULARY_ID);
        addEntryToVocabulary(vocabId, FIRST_ENTRY_PAYLOAD);

        // when
        var updatedEntryPayload = """
                {
                    "sysvocab_key": "key",
                    "sysvocab_label": "  "
                }
                """;
        checkResponseWithPayload(BAD_REQUEST, PUT, MediaType.APPLICATION_JSON, REPO_ID, updatedEntryPayload,
                VOCABULARY_PATH + "/" + vocabId).andExpect(
                jsonPath(MESSAGE).value("Vocabulary entry label is null or blank"));
    }

    @Test
    void testUpdateVocabularyEntryWhenThereIsNoVocabulary() throws Exception {
        var updatedEntryPayload = """
                {
                    "sysvocab_key": "myVocabularyKey",
                    "sysvocab_label": "updatedVocabularyLabel"
                }
                """;
        checkResponseWithPayload(NOT_FOUND, PUT, MediaType.APPLICATION_JSON, REPO_ID, updatedEntryPayload,
                VOCABULARY_PATH + "/" + UUID.randomUUID());
    }

    @Test
    void testUpdateVocabularyEntryWhenThereIsNoEntry() throws Exception {
        var result = createVocabulary();
        var vocabId = getJsonPath(result, VOCABULARY_ID);
        var updatedEntryPayload = """
                {
                    "sysvocab_key": "myVocabularyKey",
                    "sysvocab_label": "updatedVocabularyLabel"
                }
                """;
        checkResponseWithPayload(NOT_FOUND, PUT, MediaType.APPLICATION_JSON, REPO_ID, updatedEntryPayload,
                VOCABULARY_PATH + "/" + vocabId);
    }

    @AfterEach
    public void tearDown() {
        var vocabulariesSession = getVocabulariesSession();
        vocabulariesSession.query("SELECT * FROM " + VocabularyConstants.VOCABULARY_ENTRY_TYPE)
                           .stream()
                           .map(DocumentModel::getRef)
                           .forEach(vocabulariesSession::removeDocument);
    }

    private CoreSession getVocabulariesSession() {
        return CoreInstance.getCoreSession(coreSession.getRepositoryName() + VOCABULARY_REPOSITORY_SUFFIX,
                coreSession.getPrincipal());
    }

    private ResultActions createVocabulary() throws Exception {
        return checkResponseWithPayload(CREATED, POST, MediaType.APPLICATION_JSON, REPO_ID, MY_VOCABULARY_PAYLOAD,
                VOCABULARY_PATH);
    }

    private ResultActions addEntryToVocabulary(String vocabId, String entryPayload) throws Exception {
        return checkResponseWithPayload(CREATED, POST, MediaType.APPLICATION_JSON, REPO_ID, entryPayload,
                VOCABULARY_PATH + "/" + vocabId);
    }

}
