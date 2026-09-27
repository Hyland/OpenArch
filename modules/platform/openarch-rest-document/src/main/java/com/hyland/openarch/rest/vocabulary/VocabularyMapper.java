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
package com.hyland.openarch.rest.vocabulary;

import com.hyland.openarch.ecm.platform.vocabulary.model.CreateVocabulary;
import com.hyland.openarch.ecm.platform.vocabulary.model.CreateVocabularyEntry;
import com.hyland.openarch.ecm.platform.vocabulary.model.VocabularyEntryWrapper;
import com.hyland.openarch.ecm.platform.vocabulary.model.VocabularyWrapper;
import com.hyland.openarch.rest.vocabulary.io.VocabularyDTO;
import com.hyland.openarch.rest.vocabulary.io.VocabularyEntryRequestDTO;
import com.hyland.openarch.rest.vocabulary.io.VocabularyEntryResponseDTO;
import com.hyland.openarch.rest.vocabulary.io.VocabularyIdDTO;
import org.springframework.stereotype.Component;

@Component
public class VocabularyMapper {

    VocabularyDTO toVocabularyDto(VocabularyWrapper vocabulary) {
        return new VocabularyDTO(vocabulary.getVocabularyId());
    }

    CreateVocabulary toVocabulary(VocabularyDTO vocabularyDto) {
        return new CreateVocabulary(vocabularyDto.vocabId());
    }

    VocabularyIdDTO vocabularyWrapperToVocabularyIdResponse(VocabularyWrapper voc) {
        return new VocabularyIdDTO(voc.getVocabularyId());
    }

    VocabularyDTO vocabularyWrapperToVocabularyResponse(VocabularyWrapper voc) {
        return new VocabularyDTO(voc.getVocabularyId());
    }

    VocabularyEntryResponseDTO vocabularyEntryWrapperToVocabularyEntryResponse(VocabularyEntryWrapper entry) {
        return new VocabularyEntryResponseDTO.Builder().withVocabularyKey(entry.getKey())
                                                       .withVocabularyLabel(entry.getLabel())
                                                       .withVocabularyId(entry.getVocabularyId())
                                                       .build();
    }

    CreateVocabularyEntry vocabularyEntryRequestToCreateVocabularyEntry(VocabularyEntryRequestDTO vocabularyEntryDTO,
            VocabularyDTO parent) {
        return new CreateVocabularyEntry(parent.vocabId(), vocabularyEntryDTO.vocabularyKey(),
                vocabularyEntryDTO.vocabularyLabel());
    }

}
