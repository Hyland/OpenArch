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
package com.hyland.openarch.ecm.platform.vocabulary;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.hyland.openarch.ecm.core.api.VocabularyConstants.VOCABULARY_REPOSITORY_SUFFIX;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.CoreInstance;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.PathRef;
import com.hyland.openarch.ecm.core.api.VocabularyConstants;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.ecm.platform.vocabulary.exception.VocabularyEntryNotFoundException;
import com.hyland.openarch.ecm.platform.vocabulary.exception.VocabularyNotFoundException;
import com.hyland.openarch.ecm.platform.vocabulary.model.CreateVocabulary;
import com.hyland.openarch.ecm.platform.vocabulary.model.CreateVocabularyEntry;
import com.hyland.openarch.ecm.platform.vocabulary.model.VocabularyEntryWrapper;
import com.hyland.openarch.ecm.platform.vocabulary.model.VocabularyWrapper;
import com.hyland.openarch.runtime.mockito.RuntimeInject;
import com.hyland.openarch.runtime.springboot.junit.RuntimeSpringBootTest;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import org.springframework.boot.test.context.SpringBootTest;

@Features(CoreFeature.class)
@Deploy("org.nuxeo.ecm.platform.vocabularies")
@SpringBootTest(classes = { VocabularyService.class, InternalVocabularyMapper.class })
public class VocabularyServiceTest extends RuntimeSpringBootTest {

    private static final String VOCABULARY_ID = "new-voc";

    @Inject
    VocabularyService service;

    @RuntimeInject
    CoreSession session;

    @AfterEach
    public void tearDown() {
        var vocabulariesSession = getVocabulariesSession();
        vocabulariesSession.query("SELECT * FROM " + VocabularyConstants.VOCABULARY_ENTRY_TYPE)
               .stream()
               .map(DocumentModel::getRef)
               .forEach(vocabulariesSession::removeDocument);
    }

    private CoreSession getVocabulariesSession() {
        return CoreInstance.getCoreSession(session.getRepositoryName() + VOCABULARY_REPOSITORY_SUFFIX,
                session.getPrincipal());
    }

    @Test
    public void serviceCanCreateNewVocabulary() {
        // When creating a new vocabulary
        var result = service.createVocabulary(session.getRepositoryName(), new CreateVocabulary(VOCABULARY_ID));

        // Then, the vocabulary has been created
        assertEquals(VOCABULARY_ID, result.getVocabularyId());
        assertTrue(getVocabulariesSession().exists(new PathRef("/" + VOCABULARY_ID)));
    }

    @Test
    public void serviceCanCreateNewVocabularyEntry() {
        // Given a created vocabulary
        service.createVocabulary(session.getRepositoryName(), new CreateVocabulary(VOCABULARY_ID));

        // When creating a new vocabulary entry
        var result = service.createVocabularyEntry(session.getRepositoryName(),
                new CreateVocabularyEntry(VOCABULARY_ID, "id123", "Label 123"));

        // Then, the vocabulary entry has been created
        assertEquals(VOCABULARY_ID, result.getVocabularyId());
        assertEquals("id123", result.getKey());
        assertEquals("Label 123", result.getLabel());
        assertTrue(getVocabulariesSession().exists(new PathRef("/%s/%s".formatted(VOCABULARY_ID, "id123"))));
    }

    @Test
    public void serviceCanGetExistingVocabulary() {
        // Given a created vocabulary
        service.createVocabulary(session.getRepositoryName(), new CreateVocabulary(VOCABULARY_ID));

        // When getting the vocabulary
        var result = service.getVocabulary(session.getRepositoryName(), VOCABULARY_ID);

        // Then the vocabulary is returned
        assertEquals(VOCABULARY_ID, result.getVocabularyId());
    }

    @Test
    public void serviceCanListVocabularies() {
        // Given several created vocabularies
        service.createVocabulary(session.getRepositoryName(), new CreateVocabulary(VOCABULARY_ID));
        service.createVocabulary(session.getRepositoryName(), new CreateVocabulary("third-voc"));
        service.createVocabulary(session.getRepositoryName(), new CreateVocabulary("second-voc"));

        // When listing the vocabularies
        var result = service.getVocabularies(session.getRepositoryName());

        // Then it contains all created vocabularies in the proper order
        assertEquals(3, result.size());
        var listVocIds = result.stream().map(VocabularyWrapper::getVocabularyId).toList();
        assertTrue(listVocIds.contains(VOCABULARY_ID));
        assertTrue(listVocIds.contains("second-voc"));
        assertTrue(listVocIds.contains("third-voc"));
    }

    @Test
    public void serviceCanGetExistingVocabularyEntry() {
        // Given two created vocabularies with a few entries
        service.createVocabulary(session.getRepositoryName(), new CreateVocabulary(VOCABULARY_ID));
        service.createVocabulary(session.getRepositoryName(), new CreateVocabulary("second-voc"));
        service.createVocabularyEntry(session.getRepositoryName(),
                new CreateVocabularyEntry(VOCABULARY_ID, "id123", "Label 123"));
        service.createVocabularyEntry(session.getRepositoryName(),
                new CreateVocabularyEntry(VOCABULARY_ID, "id234", "Label 234"));
        service.createVocabularyEntry(session.getRepositoryName(),
                new CreateVocabularyEntry("second-voc", "id345", "Label 345"));
        service.createVocabularyEntry(session.getRepositoryName(),
                new CreateVocabularyEntry("second-voc", "id234", "Label2 234"));

        // When getting a vocabulary entry
        VocabularyEntryWrapper result = service.getVocabularyEntry(session.getRepositoryName(), VOCABULARY_ID, "id234");

        // Then the entry is returned
        assertEquals(VOCABULARY_ID, result.getVocabularyId());
        assertEquals("id234", result.getKey());
        assertEquals("Label 234", result.getLabel());
    }

    @Test
    public void serviceCanListVocabularyEntries() {
        // Given two created vocabularies with a few entries
        service.createVocabulary(session.getRepositoryName(), new CreateVocabulary(VOCABULARY_ID));
        service.createVocabulary(session.getRepositoryName(), new CreateVocabulary("second-voc"));
        var entry1 = service.createVocabularyEntry(session.getRepositoryName(),
                new CreateVocabularyEntry(VOCABULARY_ID, "id123", "Label 123"));
        var entry2 = service.createVocabularyEntry(session.getRepositoryName(),
                new CreateVocabularyEntry(VOCABULARY_ID, "id012", "Label 012"));
        service.createVocabularyEntry(session.getRepositoryName(),
                new CreateVocabularyEntry("second-voc", "id234", "Label2 234"));

        // When listing the entries of a vocabulary
        var result = service.getVocabularyEntries(session.getRepositoryName(), VOCABULARY_ID);

        // Then all proper entries are returned in the proper order (by vocabulary key)
        assertEquals(2, result.size());
        assertTrue(result.containsAll(asList(entry2, entry1)));
    }

    @Test
    public void serviceCanDeleteVocabulary() {
        // Given two created vocabularies
        service.createVocabulary(session.getRepositoryName(), new CreateVocabulary(VOCABULARY_ID));
        service.createVocabulary(session.getRepositoryName(), new CreateVocabulary("second-voc"));

        // When deleting a vocabulary
        service.removeVocabulary(session.getRepositoryName(), VOCABULARY_ID);

        // Then the vocabulary is deleted
        assertEquals(1, service.getVocabularies(session.getRepositoryName()).size());
        assertFalse(session.exists(new PathRef("/" + VOCABULARY_ID)));
    }

    @Test
    public void serviceCanDeleteVocabularyEntry() {
        // Given two created vocabularies with a few entries
        service.createVocabulary(session.getRepositoryName(), new CreateVocabulary(VOCABULARY_ID));
        service.createVocabulary(session.getRepositoryName(), new CreateVocabulary("second-voc"));
        service.createVocabularyEntry(session.getRepositoryName(),
                new CreateVocabularyEntry(VOCABULARY_ID, "id123", "Label 123"));
        service.createVocabularyEntry(session.getRepositoryName(),
                new CreateVocabularyEntry(VOCABULARY_ID, "id234", "Label 234"));
        service.createVocabularyEntry(session.getRepositoryName(),
                new CreateVocabularyEntry("second-voc", "id234", "Label2 234"));

        // When deleting a vocabulary entry
        service.removeVocabularyEntry(session.getRepositoryName(), VOCABULARY_ID, "id234");

        // Then the entry is deleted
        assertEquals(1, service.getVocabularyEntries(session.getRepositoryName(), VOCABULARY_ID).size());
        assertFalse(getVocabulariesSession().exists(new PathRef("/%s/%s".formatted(VOCABULARY_ID, "id234"))));
    }

    @Test
    public void serviceThrowsExceptionIfVocabularyDoesNotExist() {
        // Given no vocabularies created
        String repoName = session.getRepositoryName();
        // When getting an unknown vocabulary
        assertThrows(VocabularyNotFoundException.class, () -> service.getVocabulary(repoName, "unknown"));
        // When getting an entry from an unknown vocabulary
        assertThrows(VocabularyNotFoundException.class, () -> service.getVocabularyEntry(repoName, "unknown", "id123"));
    }

    @Test
    public void serviceThrowsExceptionIfEntryDoesNotExist() {
        String repoName = session.getRepositoryName();
        // Given a created vocabulary
        service.createVocabulary(repoName, new CreateVocabulary(VOCABULARY_ID));

        // When getting an unknown entry from the vocabulary, then an exception is thrown
        assertThrows(VocabularyEntryNotFoundException.class,
                () -> service.getVocabularyEntry(repoName, VOCABULARY_ID, "unknown"));
    }
}
