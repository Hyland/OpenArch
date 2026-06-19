/*
 * (C) Copyright 2024 Hyland (http://www.hyland.com/) and others.
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
 *     Gildas Lefevre
 *     janv
 */

package org.nuxeo.rest.vocabulary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.api.VocabularyConstants.VOCABULARY_REPOSITORY_SUFFIX;
import static org.nuxeo.ecm.core.schema.types.resolver.ObjectResolver.NAME_KEY;
import static org.nuxeo.ecm.platform.vocabulary.VocabularyEntryResolver.PARAM_REPOSITORY;
import static org.nuxeo.ecm.platform.vocabulary.VocabularyEntryResolver.PARAM_VOCABULARY;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.security.auth.login.LoginException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.SystemPrincipal;
import org.nuxeo.ecm.core.api.VocabularyConstants;
import org.nuxeo.ecm.core.api.validation.DocumentValidationService;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.platform.vocabulary.VocabularyEntryResolver;
import org.nuxeo.ecm.platform.vocabulary.VocabularyService;
import org.nuxeo.ecm.platform.vocabulary.model.CreateVocabulary;
import org.nuxeo.ecm.platform.vocabulary.model.CreateVocabularyEntry;
import org.nuxeo.ecm.platform.vocabulary.model.VocabularyEntryWrapper;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.api.login.LoginContext;
import org.nuxeo.runtime.mockito.RuntimeInject;
import org.nuxeo.runtime.springboot.junit.RuntimeSpringBootTest;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Features(CoreFeature.class)
@Deploy("org.nuxeo.ecm.platform.vocabularies")
@Deploy("org.nuxeo.springboot.rest.document.test:OSGI-INF/test-vocabulary-resolver-contrib.xml")
@Import({ VocabularyAPITestConfiguration.class })
@SpringBootTest(classes = { VocabularyAPI.class })
public class VocabularyEntryResolverTest extends RuntimeSpringBootTest {

    private static final String REFERENCED_DIRECTORY2 = "referencedDirectory2";

    private static final String REFERENCED_DIRECTORY1 = "referencedDirectory1";

    private static final String REF1_XPATH = "vr_vocabulary1Ref";

    private static final String REF2_XPATH = "vr_vocabulary2Ref";

    private static final String ENTRY_ID = "123";

    private static final String ENTRY_LABEL = "Label123";

    private static final String REPO_TEST = "test";

    @RuntimeInject
    protected CoreSession coreSession;

    @Autowired
    protected VocabularyService vocabularyService;

    protected DocumentModel doc;

    protected VocabularyEntryWrapper entry1;

    @BeforeEach
    public void setup() throws Exception {
        // Create first the vocabularies
        vocabularyService.createVocabulary(coreSession.getRepositoryName(),
                new CreateVocabulary(REFERENCED_DIRECTORY1));
        entry1 = vocabularyService.createVocabularyEntry(coreSession.getRepositoryName(),
                new CreateVocabularyEntry(REFERENCED_DIRECTORY1, "123", "Label123"));
        vocabularyService.createVocabularyEntry(coreSession.getRepositoryName(),
                new CreateVocabularyEntry(REFERENCED_DIRECTORY1, "234", "Label234"));
        vocabularyService.createVocabularyEntry(coreSession.getRepositoryName(),
                new CreateVocabularyEntry(REFERENCED_DIRECTORY1, "345", "Label345"));
        vocabularyService.createVocabularyEntry(coreSession.getRepositoryName(),
                new CreateVocabularyEntry(REFERENCED_DIRECTORY1, "456", "Label456"));
        vocabularyService.createVocabularyEntry(coreSession.getRepositoryName(),
                new CreateVocabularyEntry(REFERENCED_DIRECTORY1, "678", "label.test.translated.entry"));
        vocabularyService.createVocabularyEntry(coreSession.getRepositoryName(),
                new CreateVocabularyEntry(REFERENCED_DIRECTORY1, "789", "123"));

        vocabularyService.createVocabulary(coreSession.getRepositoryName(),
                new CreateVocabulary(REFERENCED_DIRECTORY2));

        doc = coreSession.createDocumentModel("/", "doc1", "DirectoryReferencer");
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

    @Test
    public void supportedClasses() {
        List<Class<?>> classes = new VocabularyEntryResolver().getManagedClasses();
        assertEquals(1, classes.size());
        assertTrue(classes.contains(VocabularyEntryWrapper.class));
    }

    @Test
    public void testLifecycleNoConfigurationFetch() {
        VocabularyEntryResolver resolver = new VocabularyEntryResolver();
        assertThrows(IllegalStateException.class, () -> resolver.fetch(ENTRY_ID));

    }

    @Test
    public void testLifecycleNoConfigurationFetchCast() {
        VocabularyEntryResolver resolver = new VocabularyEntryResolver();
        assertThrows(IllegalStateException.class, () -> resolver.fetch(DocumentModel.class, ENTRY_ID));
    }

    @Test
    public void testLifecycleNoConfigurationGetReference() {
        VocabularyEntryResolver resolver = new VocabularyEntryResolver();
        assertThrows(IllegalStateException.class, () -> resolver.getReference(entry1));
    }

    @Test
    public void testLifecycleNoConfigurationGetParameters() {
        VocabularyEntryResolver resolver = new VocabularyEntryResolver();
        assertThrows(IllegalStateException.class, () -> resolver.getParameters());
    }

    @Test
    public void testLifecycleNoConfigurationGetConstraintErrorMessage() {
        VocabularyEntryResolver resolver = new VocabularyEntryResolver();
        assertThrows(IllegalStateException.class, () -> resolver.getConstraintErrorMessage(null, Locale.ENGLISH));
    }

    @Test
    public void testLifecycleConfigurationTwice() {
        VocabularyEntryResolver derr = new VocabularyEntryResolver();
        HashMap<String, String> parameters = new HashMap<>();
        parameters.put(PARAM_VOCABULARY, REFERENCED_DIRECTORY1);
        derr.configure(parameters);
        assertThrows(IllegalStateException.class, () -> {
            derr.configure(parameters);
        });
    }

    @Test
    public void testConfigurationDir1() {
        VocabularyEntryResolver derr = new VocabularyEntryResolver();
        HashMap<String, String> parameters = new HashMap<>();
        parameters.put(PARAM_VOCABULARY, REFERENCED_DIRECTORY1);
        parameters.put(PARAM_REPOSITORY, REPO_TEST);
        derr.configure(parameters);
        assertEquals(REFERENCED_DIRECTORY1, derr.getVocabulary().getVocabularyId());
        Map<String, Object> outputParameters = derr.getParameters();
        assertEquals(REFERENCED_DIRECTORY1, outputParameters.get(PARAM_VOCABULARY));
    }

    @Test
    public void testConfigurationMissingDirectory() {
        VocabularyEntryResolver derr = new VocabularyEntryResolver();
        Map prop = new HashMap<>();
        assertThrows(IllegalArgumentException.class, () -> {
            derr.configure(prop);
        });
    }

    @Test
    public void testConfigurationNonExistingDirectory() {
        VocabularyEntryResolver derr = new VocabularyEntryResolver();
        HashMap<String, String> parameters = new HashMap<>();
        parameters.put(PARAM_VOCABULARY, "aBadDirectoryName");
        parameters.put(PARAM_REPOSITORY, REPO_TEST);
        derr.configure(parameters);
        assertThrows(IllegalArgumentException.class, () -> {
            derr.validate(ENTRY_ID);
        });
    }

    @Test
    public void testValidateGoodDir1Ref() {
        VocabularyEntryResolver derr = new VocabularyEntryResolver();
        HashMap<String, String> parameters = new HashMap<>();
        parameters.put(PARAM_VOCABULARY, REFERENCED_DIRECTORY1);
        parameters.put(PARAM_REPOSITORY, REPO_TEST);
        derr.configure(parameters);
        assertTrue(derr.validate(ENTRY_ID));
    }

    @Test
    public void testValidateDir1RefFailedWithBadValue() {
        VocabularyEntryResolver derr = new VocabularyEntryResolver();
        HashMap<String, String> parameters = new HashMap<>();
        parameters.put(PARAM_VOCABULARY, REFERENCED_DIRECTORY1);
        parameters.put(PARAM_REPOSITORY, REPO_TEST);
        derr.configure(parameters);
        assertFalse(derr.validate("BAD id !"));
    }

    @Test
    public void testFetchGoodDir1Ref() {
        VocabularyEntryResolver derr = new VocabularyEntryResolver();
        HashMap<String, String> parameters = new HashMap<>();
        parameters.put(PARAM_VOCABULARY, REFERENCED_DIRECTORY1);
        parameters.put(PARAM_REPOSITORY, REPO_TEST);
        derr.configure(parameters);
        Object entity = derr.fetch(ENTRY_ID);
        assertTrue(entity instanceof VocabularyEntryWrapper);
        assertEquals(ENTRY_LABEL, ((VocabularyEntryWrapper) entity).getLabel());
    }

    @Test
    public void testFetchDir1RefFailedWithBadValue() {
        VocabularyEntryResolver derr = new VocabularyEntryResolver();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(PARAM_VOCABULARY, REFERENCED_DIRECTORY1);
        parameters.put(PARAM_REPOSITORY, REPO_TEST);
        derr.configure(parameters);
        assertNull(derr.fetch("BAD id !"));
    }

    @Test
    public void testFetchCastVocabularyEntryWrapper() {
        VocabularyEntryResolver derr = new VocabularyEntryResolver();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(PARAM_VOCABULARY, REFERENCED_DIRECTORY1);
        parameters.put(PARAM_REPOSITORY, REPO_TEST);
        derr.configure(parameters);
        VocabularyEntryWrapper document = derr.fetch(VocabularyEntryWrapper.class, ENTRY_ID);
        assertNotNull(document);
        assertEquals(ENTRY_LABEL, document.getLabel());
        assertNull(derr.fetch(VocabularyEntryWrapper.class, "toto"));
    }

    @Test
    public void testFetchCastDoesntSupportStupidTypes() {
        VocabularyEntryResolver derr = new VocabularyEntryResolver();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(PARAM_VOCABULARY, REFERENCED_DIRECTORY1);
        parameters.put(PARAM_REPOSITORY, REPO_TEST);
        derr.configure(parameters);
        assertNull(derr.fetch(List.class, ENTRY_ID));
    }

    @Test
    public void testGetReferenceDir1Ref() {
        VocabularyEntryResolver derr = new VocabularyEntryResolver();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(PARAM_VOCABULARY, REFERENCED_DIRECTORY1);
        parameters.put(PARAM_REPOSITORY, REPO_TEST);
        derr.configure(parameters);
        assertEquals(ENTRY_ID, derr.getReference(entry1));
    }

    @Test
    public void testGetReferenceInvalid() {
        VocabularyEntryResolver derr = new VocabularyEntryResolver();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(PARAM_VOCABULARY, REFERENCED_DIRECTORY1);
        parameters.put(PARAM_REPOSITORY, REPO_TEST);
        derr.configure(parameters);
        assertNull(derr.getReference("nothing"));
    }

    @Test
    public void testConfigurationIsLoaded() {
        VocabularyEntryResolver idResolver = (VocabularyEntryResolver) coreSession.getSchemaManager()
                                                                                  .getField(REF1_XPATH)
                                                                                  .getType()
                                                                                  .getObjectResolver();
        assertEquals(REFERENCED_DIRECTORY1, idResolver.getVocabulary().getVocabularyId());
        assertEquals(REFERENCED_DIRECTORY1, idResolver.getParameters().get(PARAM_VOCABULARY));
        assertEquals(REPO_TEST, idResolver.getParameters().get(PARAM_REPOSITORY));
        VocabularyEntryResolver pathResolver = (VocabularyEntryResolver) coreSession.getSchemaManager()
                                                                                    .getField(REF2_XPATH)
                                                                                    .getType()
                                                                                    .getObjectResolver();
        assertEquals(REFERENCED_DIRECTORY2, pathResolver.getVocabulary().getVocabularyId());
        assertEquals(REFERENCED_DIRECTORY2, pathResolver.getParameters().get(PARAM_VOCABULARY));
        assertEquals(REPO_TEST, pathResolver.getParameters().get(PARAM_REPOSITORY));
    }

    @Test
    public void testNullValueReturnNull() {
        assertNull(doc.getObjectResolver(REF1_XPATH).fetch());
        assertNull(doc.getObjectResolver(REF1_XPATH).fetch(DocumentModel.class));
        assertNull(doc.getObjectResolver(REF2_XPATH).fetch());
        assertNull(doc.getObjectResolver(REF2_XPATH).fetch(DocumentModel.class));
    }

    @Test
    public void testBadValuesValidationFailed() {
        doc.setPropertyValue(REF1_XPATH, "BAD id !");
        assertNull(doc.getObjectResolver(REF1_XPATH).fetch());
        assertFalse(doc.getObjectResolver(REF1_XPATH).validate());
        doc.setPropertyValue(REF2_XPATH, "BAD path !");
        assertNull(doc.getObjectResolver(REF2_XPATH).fetch());
        assertFalse(doc.getObjectResolver(REF2_XPATH).validate());
        assertEquals(2, Framework.getService(DocumentValidationService.class).validate(doc).numberOfViolations());
    }

    @Test
    public void testRefCorrectValues() {
        doc.setPropertyValue(REF1_XPATH, ENTRY_ID);
        VocabularyEntryWrapper entry = (VocabularyEntryWrapper) doc.getObjectResolver(REF1_XPATH).fetch();
        assertNotNull(entry);
        assertEquals(ENTRY_LABEL, entry.getLabel());
        entry = doc.getObjectResolver(REF1_XPATH).fetch(VocabularyEntryWrapper.class);
        assertNotNull(entry);
        assertEquals(ENTRY_LABEL, entry.getLabel());
    }

    @Test
    public void testTranslation() {
        VocabularyEntryResolver idderr = new VocabularyEntryResolver();
        idderr.configure(Map.of(NAME_KEY, "vocabularyResolver", PARAM_VOCABULARY, REFERENCED_DIRECTORY1,
                PARAM_REPOSITORY, REPO_TEST));
        checkMessage("\"abc123\" is not a valid referencedDirectory1 id.", idderr);

        VocabularyEntryResolver pathderr = new VocabularyEntryResolver();
        pathderr.configure(Map.of(NAME_KEY, "vocabularyResolver", PARAM_VOCABULARY, REFERENCED_DIRECTORY2,
                PARAM_REPOSITORY, REPO_TEST));
        checkMessage("\"abc123\" is not a valid referencedDirectory2 id.", pathderr);

        VocabularyEntryResolver pathderr2 = new VocabularyEntryResolver();
        pathderr2.configure(
                Map.of(NAME_KEY, "myResolver", PARAM_VOCABULARY, REFERENCED_DIRECTORY2, PARAM_REPOSITORY, REPO_TEST));
        checkMessage("myResolver cannot resolve reference abc123", pathderr2);
    }

    @Test
    public void testFetchDirWhenUnauthenticated() throws LoginException {

        LoginContext.popPrincipal();
        try {
            VocabularyEntryResolver derr = new VocabularyEntryResolver();
            HashMap<String, String> parameters = new HashMap<>();
            parameters.put(PARAM_VOCABULARY, REFERENCED_DIRECTORY1);
            parameters.put(PARAM_REPOSITORY, REPO_TEST);
            derr.configure(parameters);
            Object entity = derr.fetch(ENTRY_ID);
            assertNotNull(entity);
            assertTrue(entity instanceof VocabularyEntryWrapper);
            assertEquals(ENTRY_LABEL, ((VocabularyEntryWrapper) entity).getLabel());
        } finally {
            LoginContext.pushPrincipal(new SystemPrincipal());
        }
    }

    private void checkMessage(String expected, VocabularyEntryResolver derr) {
        assertEquals(expected, derr.getConstraintErrorMessage("abc123", Locale.ENGLISH));
    }

}
