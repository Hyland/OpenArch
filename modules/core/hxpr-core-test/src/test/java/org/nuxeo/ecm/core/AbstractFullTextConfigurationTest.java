/*
 * (C) Copyright 2023 Hyland (https://www.hyland.com/) and others.
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
 */
package org.nuxeo.ecm.core;

import static org.awaitility.Awaitility.await;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.SchemaManagerDTO;
import org.nuxeo.ecm.core.schema.TypeService;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.mockito.RuntimeInject;
import org.nuxeo.runtime.springboot.junit.RuntimeSpringBootTest;
import org.nuxeo.runtime.transaction.TransactionHelper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.JsonPatch;

/**
 * Abstract class for tests requiring a full text configuration. It updates the model definition to create a new
 * Document type called FileFullText with the mixin SysFullTextIndexable and overrides the schema file in order to define
 * the 'file_blob' with the flag 'fullTextIndexed'.
 */
@Tag("useDocker")
public abstract class AbstractFullTextConfigurationTest extends RuntimeSpringBootTest {

    public static final String DOCUMENT_TYPE_TEST = "FileFullText";

    @RuntimeInject
    protected CoreFeature coreFeature;

    @RuntimeInject
    protected CoreSession session;

    @RuntimeInject
    private TypeService typeService;

    private static final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    public void setupModelConfig() throws Exception {
        // Define a new DocumentType with the fullTextIndexed flag
        var schemaManager = typeService.getSchemaManager(session.getRepositoryName());
        var schemaManagerDTO = new SchemaManagerDTO(updateModelDTO(schemaManager), null, false);
        typeService.setSchemaManager(session.getRepositoryName(), schemaManagerDTO);
        commitAndStartNewTransaction();
    }

    @AfterEach
    public void tearDown() {
        session.query("SELECT * FROM SysContent WHERE sys_primaryType != 'SysFolder' AND sysver_isVersion = FALSE")
               .stream()
               .map(DocumentModel::getRef)
               .forEach(session::removeDocument);
        // Remove first folders that are children of other Folder documents
        session.query("SELECT * FROM SysFolder ORDER BY sys_path DESC")
               .stream()
               .map(DocumentModel::getRef)
               .forEach(session::removeDocument);
    }

    private Map<String, Object> updateModelDTO(SchemaManager schemaManager) throws Exception {
        // Update the 'file' schema in order to add the flag 'fullTextIndexed' on the field 'file_blob'
        // Add a new primary type
        var patch = """
                [
                    {
                        "op": "replace",
                        "path": "/schemas/sysfile/fields/sysfile_blob",
                        "value": {
                            "extends": "blob",
                            "fullTextIndexed": true
                        }
                    },{
                        "op": "add",
                        "path": "/primaryTypes/FileFullText",
                        "value": {
                            "extends": "SysContent",
                            "mixins": [
                                "SysFullTextIndexable",
                                "SysVersionable"
                            ],
                            "schemas": [
                                "system",
                                "sysfile",
                                "sysfiles"
                            ]
                        }
                    },{
                        "op": "add",
                        "path": "/primaryTypes/FileFullTextNew",
                        "value": {
                            "extends": "SysContent",
                            "mixins": ["SysFullTextIndexable"],
                            "schemas": [
                                "system",
                                "sysfile",
                                "sysfiles"
                            ]
                        }
                    },{
                        "op": "add",
                        "path": "/mixinTypes/SysFullTextIndexable",
                        "value": { }
                    }
                ]
                """;

        var jsonPatch = mapper.readTree(patch);
        var patched = JsonPatch.apply(jsonPatch, mapper.valueToTree(schemaManager.toDTO()));
        return mapper.convertValue(patched, Map.class);
    }

    private Map revertModelDTO(SchemaManager schemaManager) throws Exception {
        // DTO to remove the new primary type with the fullTextIndexed flag
        var patch = """
                [
                    {
                        "op": "remove",
                        "path": "/primaryTypes/FileFullTextNew"
                    }
                ]
                """;

        var jsonPatch = mapper.readTree(patch);
        var patched = JsonPatch.apply(jsonPatch, mapper.valueToTree(schemaManager.toDTO()));
        return mapper.convertValue(patched, Map.class);
    }

    public void revertModelConfig() throws Exception {
        // revert a new DocumentType with the fullTextIndexed flag
        var schemaManager = typeService.getSchemaManager(session.getRepositoryName());
        var schemaManagerDTO = new SchemaManagerDTO(revertModelDTO(schemaManager), null, false);
        typeService.setSchemaManager(session.getRepositoryName(), schemaManagerDTO);
        commitAndStartNewTransaction();
    }

    protected void waitForFulltextIndexing() {
        commitAndStartNewTransaction();
        coreFeature.getStorageConfiguration().waitForFulltextIndexing();
    }

    // Unlike waitForFulltextIndexing this method waits for the async reply from the Transform Service to be processed.
    public String waitForFulltext(int timeoutSeconds, String repositoryName, DocumentRef docRef) {
        coreFeature.waitForAsyncCompletion();
        AtomicReference<String> text = new AtomicReference<>();
        awaitReply(timeoutSeconds, () -> {
            text.set(getBinaryFulltext(repositoryName, docRef));
            return text.get() != null;
        });
        return text.get();
    }

    private void awaitReply(int timeoutSeconds, Callable<Boolean> conditionEvaluator) {
        await().atMost(Math.max(timeoutSeconds, 1), TimeUnit.SECONDS)
               .pollInterval(100, TimeUnit.MILLISECONDS)
               .until(conditionEvaluator);
    }

    private String getBinaryFulltext(String repositoryName, DocumentRef docRef) {
        return TransactionHelper.runInNewTransaction(() -> {
            try (var __ = Framework.loginSystem()) {
                return CoreInstance.doPrivileged(repositoryName, session -> {
                    return session.getBinaryFulltext(docRef);
                });
            }
        });
    }

    protected void commitAndStartNewTransaction() {
        if (TransactionHelper.isTransactionActiveOrMarkedRollback()) {
            TransactionHelper.commitOrRollbackTransaction();
            TransactionHelper.startTransaction();
        }
    }
}
