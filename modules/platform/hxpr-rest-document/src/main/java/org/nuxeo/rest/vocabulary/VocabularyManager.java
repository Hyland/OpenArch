/*
 * (C) Copyright 2022 Hyland (https://www.hyland.com) and others.
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
 *     Krzysiek Krolewski
 *     Adam Dunajewski
 */

package org.nuxeo.rest.vocabulary;

import java.util.List;

import org.nuxeo.ecm.platform.vocabulary.VocabularyService;
import org.nuxeo.rest.vocabulary.io.VocabularyDTO;
import org.nuxeo.rest.vocabulary.io.VocabularyEntryRequestDTO;
import org.nuxeo.rest.vocabulary.io.VocabularyEntryResponseDTO;
import org.nuxeo.rest.vocabulary.io.VocabularyIdDTO;
import org.nuxeo.runtime.springboot.transaction.Transactional;
import org.springframework.stereotype.Component;

@Component
public class VocabularyManager {

    private final VocabularyMapper vocabularyMapper;

    private final VocabularyService vocabularyService;

    public VocabularyManager(VocabularyMapper vocabularyMapper, VocabularyService vocabularyService) {
        this.vocabularyMapper = vocabularyMapper;
        this.vocabularyService = vocabularyService;
    }

    @Transactional
    public VocabularyDTO createVocabulary(String repoId, VocabularyDTO vocabularyDto) {
        return vocabularyMapper.toVocabularyDto(
                vocabularyService.createVocabulary(repoId, vocabularyMapper.toVocabulary(vocabularyDto)));
    }

    @Transactional
    public VocabularyEntryResponseDTO createVocabularyEntry(String repoId, String vocabularyId,
            VocabularyEntryRequestDTO entry) {
        var vocabularyParent = vocabularyService.getVocabulary(repoId, vocabularyId);
        var vocabularyEntry = vocabularyMapper.vocabularyEntryRequestToCreateVocabularyEntry(entry,
                vocabularyMapper.vocabularyWrapperToVocabularyResponse(vocabularyParent));
        var createdEntry = vocabularyService.createVocabularyEntry(repoId, vocabularyEntry);
        return vocabularyMapper.vocabularyEntryWrapperToVocabularyEntryResponse(createdEntry);
    }

    @Transactional
    public List<VocabularyIdDTO> getVocabularyList(String repoId) {
        return vocabularyService.getVocabularies(repoId)
                                .stream()
                                .map(vocabularyMapper::vocabularyWrapperToVocabularyIdResponse)
                                .toList();
    }

    @Transactional
    public void deleteVocabulary(String repoId, String vocabularyId) {
        vocabularyService.removeVocabulary(repoId, vocabularyId);
    }

    @Transactional
    public List<VocabularyEntryResponseDTO> getVocabularyEntries(String repoId, String vocabularyId) {
        var entries = vocabularyService.getVocabularyEntries(repoId, vocabularyId);
        return entries.stream().map(vocabularyMapper::vocabularyEntryWrapperToVocabularyEntryResponse).toList();
    }

    @Transactional
    public VocabularyEntryResponseDTO getVocabularyEntry(String repoId, String vocabularyId, String entryKey) {
        var entry = vocabularyService.getVocabularyEntry(repoId, vocabularyId, entryKey);
        return vocabularyMapper.vocabularyEntryWrapperToVocabularyEntryResponse(entry);
    }

    @Transactional
    public VocabularyEntryResponseDTO updateVocabularyEntry(String repoId, String vocabularyId,
            VocabularyEntryRequestDTO entry) {
        var parent = vocabularyService.getVocabulary(repoId, vocabularyId);
        var vocParent = vocabularyMapper.vocabularyWrapperToVocabularyResponse(parent);
        var updatedEntry = vocabularyService.updateVocabularyEntry(repoId,
                vocabularyMapper.vocabularyEntryRequestToCreateVocabularyEntry(entry, vocParent));
        return vocabularyMapper.vocabularyEntryWrapperToVocabularyEntryResponse(updatedEntry);
    }

    @Transactional
    public void deleteVocabularyEntry(String repoId, String vocabularyId, String vocabularyKey) {
        vocabularyService.removeVocabularyEntry(repoId, vocabularyId, vocabularyKey);
    }
}
