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
package org.nuxeo.ecm.platform.vocabulary;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.hyland.openarch.ecm.core.schema.types.resolver.AbstractObjectResolver;
import com.hyland.openarch.ecm.core.schema.types.resolver.ObjectResolver;
import org.nuxeo.ecm.platform.vocabulary.exception.VocabularyEntryNotFoundException;
import org.nuxeo.ecm.platform.vocabulary.exception.VocabularyNotFoundException;
import org.nuxeo.ecm.platform.vocabulary.model.VocabularyEntryWrapper;
import org.nuxeo.ecm.platform.vocabulary.model.VocabularyWrapper;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.springboot.framework.ApplicationContextHelper;

/**
 * This {@link ObjectResolver} allows to manage integrity for fields containing references to vocabulary's entry.
 * <p>
 * References contains the vocabulary entry id. The repository name is not mandatory, if it defined, then the default
 * repository "default" is used.
 * <p>
 * To use it, put the following code in your schema XSD (don't forget the vocabulary name):
 *
 * <pre>
 * {@code
 * <xs:element name="carBrand">
 *   <xs:simpleType>
 *     <xs:restriction base="xs:string" ref:resolver="vocabularyResolver" ref:vocabulary=
"carBrandsVocabulary" ref:repository="default" />
 *   </xs:simpleType>
 * </xs:element>
 * }
 * </pre>
 */
public class VocabularyEntryResolver extends AbstractObjectResolver implements ObjectResolver {

    private static final long serialVersionUID = 1L;

    public static final String PARAM_VOCABULARY = "vocabulary";

    public static final String PARAM_REPOSITORY = "repository";

    private static final String DEFAULT_REPOSITORY_ID = "default";

    private List<Class<?>> managedClasses = null;

    private String vocabularyId;

    private String repositoryName;

    @Override
    public void configure(Map<String, String> parameters) throws IllegalArgumentException, IllegalStateException {
        super.configure(parameters);
        vocabularyId = parameters.get(PARAM_VOCABULARY);
        repositoryName = parameters.getOrDefault(PARAM_REPOSITORY, DEFAULT_REPOSITORY_ID);

        if (vocabularyId == null || vocabularyId.isEmpty()) {
            throw new IllegalArgumentException("missing vocabulary parameter. A vocabulary name is necessary");
        }
        this.parameters.put(PARAM_VOCABULARY, vocabularyId);
        this.parameters.put(PARAM_REPOSITORY, repositoryName);
    }

    private void checkVocabularyConfig() throws IllegalArgumentException {
        // Check that the vocabulary defined exists
        try {
            getVocabularyService().getVocabulary(repositoryName, vocabularyId);
        } catch (VocabularyNotFoundException e) {
            throw new IllegalArgumentException(
                    "The vocabulary %s does not exist in repository %s".formatted(vocabularyId, repositoryName));
        }
    }

    @Override
    public List<Class<?>> getManagedClasses() {
        if (managedClasses == null) {
            managedClasses = new ArrayList<>();
            managedClasses.add(VocabularyEntryWrapper.class);
        }
        return managedClasses;
    }

    @Override
    public Object fetch(Object value) {
        checkConfig();
        checkVocabularyConfig();
        if (value instanceof String id) {
            String finalId = id; // Effectively final
            try {
                return Framework.doPrivileged(
                        () -> getVocabularyService().getVocabularyEntry(repositoryName, vocabularyId, finalId));
            } catch (VocabularyEntryNotFoundException e) {
                return null;
            }
        }
        return null;
    }

    @Override
    public <T> T fetch(Class<T> type, Object value) {
        checkConfig();
        checkVocabularyConfig();
        VocabularyEntryWrapper de = (VocabularyEntryWrapper) fetch(value);
        if (de != null && type.isInstance(de)) {
            return type.cast(de);
        }
        return null;
    }

    @Override
    public Serializable getReference(Object entity) {
        checkConfig();
        checkVocabularyConfig();
        if (entity != null && entity instanceof VocabularyEntryWrapper entry) {
            return entry.getKey();
        }
        return null;
    }

    @Override
    public String getConstraintErrorMessage(Object invalidValue, Locale locale) {
        checkConfig();
        return getConstraintErrorMessage(null, null, invalidValue, locale, vocabularyId);
    }

    @Override
    public List<Object> getConstraintErrorParameters(Object invalidValue, Locale locale) {
        return List.of(invalidValue, vocabularyId);
    }

    public VocabularyWrapper getVocabulary() {
        return getVocabularyService().getVocabulary(repositoryName, vocabularyId);
    }

    private VocabularyService getVocabularyService() {
        return ApplicationContextHelper.getApplicationContext().getBean(VocabularyService.class);
    }
}
