/*
 * (C) Copyright 2024 Hyland (https://www.hyland.com) and others.
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
 *     Adam Dunajewski
 */
package org.nuxeo.rest.vocabulary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.nuxeo.ecm.core.api.VocabularyConstants.VOCABULARY_ID;
import static org.nuxeo.ecm.core.api.VocabularyConstants.VOCABULARY_KEY;
import static org.nuxeo.ecm.core.api.VocabularyConstants.VOCABULARY_LABEL;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.vocabulary.model.CreateVocabulary;
import org.nuxeo.ecm.platform.vocabulary.model.CreateVocabularyEntry;
import org.nuxeo.ecm.platform.vocabulary.model.VocabularyEntryWrapper;
import org.nuxeo.ecm.platform.vocabulary.model.VocabularyWrapper;
import org.nuxeo.rest.document.test.AbstractAPITest;
import org.nuxeo.rest.vocabulary.io.VocabularyDTO;
import org.nuxeo.rest.vocabulary.io.VocabularyEntryRequestDTO;
import org.nuxeo.rest.vocabulary.io.VocabularyEntryResponseDTO;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import({ VocabularyAPITestConfiguration.class })
@SpringBootTest(classes = { VocabularyAPI.class })
public class InternalVocabularyMapperTest extends AbstractAPITest {

    private final String MY_VOCABULARY_ID = "MyVocabulary";

    private final String MY_VOCABULARY_KEY = "MyVocabularyKey";

    private final String MY_VOCABULARY_LABEL = "MyVocabularyLabel";

    private VocabularyMapper mapper = new VocabularyMapper();

    @Test
    void shouldConvertVocaburalyWrapperToVocabularyResponse() {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getPropertyValue(VOCABULARY_ID)).thenReturn(MY_VOCABULARY_ID);
        VocabularyWrapper input = new VocabularyWrapper(doc);
        VocabularyDTO output = mapper.vocabularyWrapperToVocabularyResponse(input);

        assertEquals(MY_VOCABULARY_ID, output.vocabId());
    }

    @Test
    void shouldConvertVocabularyEntryWrapperToVocabularyEntryResponse() {
        DocumentModel doc = mock(DocumentModel.class);
        when(doc.getPropertyValue(VOCABULARY_ID)).thenReturn(MY_VOCABULARY_ID);
        when(doc.getPropertyValue(VOCABULARY_KEY)).thenReturn(MY_VOCABULARY_KEY);
        when(doc.getPropertyValue(VOCABULARY_LABEL)).thenReturn(MY_VOCABULARY_LABEL);
        VocabularyEntryWrapper input = new VocabularyEntryWrapper(doc);

        VocabularyEntryResponseDTO output = mapper.vocabularyEntryWrapperToVocabularyEntryResponse(input);

        assertEquals(MY_VOCABULARY_ID, output.vocabularyId());
        assertEquals(MY_VOCABULARY_KEY, output.vocabularyKey());
        assertEquals(MY_VOCABULARY_LABEL, output.vocabularyLabel());
    }

    @Test
    void shouldConvertVocabularyRequestToCreateVocabulary() {
        VocabularyDTO input = new VocabularyDTO(MY_VOCABULARY_ID);

        CreateVocabulary output = mapper.toVocabulary(input);

        assertEquals(MY_VOCABULARY_ID, output.vocabularyId());
    }

    @Test
    void shouldConvertVocabularyEntryDTOtoDocumentDTO() {
        VocabularyEntryRequestDTO input = new VocabularyEntryRequestDTO(MY_VOCABULARY_KEY, MY_VOCABULARY_LABEL);
        VocabularyDTO parent = new VocabularyDTO(MY_VOCABULARY_ID);

        CreateVocabularyEntry output = mapper.vocabularyEntryRequestToCreateVocabularyEntry(input, parent);

        assertEquals(MY_VOCABULARY_ID, output.vocabularyId());
        assertEquals(MY_VOCABULARY_KEY, output.vocabularyKey());
        assertEquals(MY_VOCABULARY_LABEL, output.vocabularyLabel());
    }
}
