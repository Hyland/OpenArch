/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.Blobs;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.blob.ManagedBlob;
import com.hyland.openarch.ecm.core.model.Repository;
import com.hyland.openarch.ecm.core.repository.RepositoryService;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.schema.SchemaManagerDTO;
import com.hyland.openarch.ecm.core.schema.TypeService;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.ecm.core.test.annotations.Granularity;
import com.hyland.openarch.ecm.core.test.annotations.RepositoryConfig;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.transaction.TransactionHelper;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.Jackson3JsonPatch;

import jakarta.inject.Inject;

/**
 * Tests that the paths to the blobs of the content model are recomputed when the content model changes while the
 * repository is running, otherwise the blobs of a newly-declared blob field would not be marked as referenced and would
 * be deleted by the blob garbage collector.
 */
@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
class TestBlobKeysPathsOnModelChange {

    private static final String MY_TYPE = "MyBlobType";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    protected CoreSession session;

    @Inject
    protected TypeService typeService;

    @Inject
    protected RepositoryService repositoryService;

    private String repositoryName;

    @BeforeEach
    void setUp() {
        repositoryName = session.getRepositoryName();
    }

    @SuppressWarnings("unchecked")
    private void patchSchemaManager(String patch) {
        SchemaManager schemaManager = typeService.getSchemaManager(repositoryName);
        Map<String, Object> map = schemaManager.toMap();
        JsonNode patched = Jackson3JsonPatch.apply(MAPPER.readTree(patch), MAPPER.valueToTree(map));
        map = MAPPER.convertValue(patched, Map.class);
        typeService.setSchemaManager(repositoryName, new SchemaManagerDTO(map));
        // commit tx so that the existing session acquires the new schema manager
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();
    }

    private List<String> getReferencedBlobKeys() {
        Repository repository = repositoryService.getRepository(repositoryName);
        List<String> keys = new ArrayList<>();
        repository.markReferencedBlobs((key, repoName) -> keys.add(key));
        return keys;
    }

    @Test
    void testBlobOfTypeAddedAfterStartupIsReferenced() {
        // add a type holding a blob, after the repository has already started
        patchSchemaManager("""
                [
                  {
                    "op": "add",
                    "path": "/schemas/myblobschema",
                    "value": {
                      "prefix": "myblob",
                      "fields": {
                        "myblob_blob": {
                          "extends": "blob"
                        }
                      }
                    }
                  },
                  {
                    "op": "add",
                    "path": "/primaryTypes/MyBlobType",
                    "value": {
                      "extends": "SysContent",
                      "schemas": ["myblobschema"]
                    }
                  }
                ]
                """);

        // create a document holding a blob in the new field
        Blob blob = Blobs.createBlob("MY BLOB CONTENT");
        DocumentModel doc = session.createDocumentModel("/", "doc", MY_TYPE);
        doc.setPropertyValue("myblob_blob", blob);
        doc.create();
        session.save();
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        ManagedBlob managedBlob = (ManagedBlob) session.getDocument(doc.getRef()).getPropertyValue("myblob_blob");
        String key = managedBlob.getKey();

        // the blob of the newly-declared field is seen as referenced, so the blob GC won't delete it
        assertTrue(getReferencedBlobKeys().contains(key), "blob key not referenced: " + key);
    }

    @Test
    void testBlobOfFieldAddedAfterStartupIsReferenced() {
        patchSchemaManager("""
                [
                  {
                    "op": "add",
                    "path": "/schemas/myblobschema2",
                    "value": {
                      "prefix": "myblob2",
                      "fields": {
                        "myblob2_string": {
                          "extends": "string"
                        }
                      }
                    }
                  },
                  {
                    "op": "add",
                    "path": "/primaryTypes/MyBlobType",
                    "value": {
                      "extends": "SysContent",
                      "schemas": ["myblobschema2"]
                    }
                  }
                ]
                """);

        // add a blob field to the schema, after a first document of the type was already created
        DocumentModel doc = session.createDocumentModel("/", "doc", MY_TYPE);
        doc.create();
        session.save();

        patchSchemaManager("""
                [
                  {
                    "op": "add",
                    "path": "/schemas/myblobschema2/fields/myblob2_blob",
                    "value": {
                      "extends": "blob"
                    }
                  }
                ]
                """);

        Blob blob = Blobs.createBlob("MY OTHER BLOB CONTENT");
        doc = session.getDocument(doc.getRef());
        doc.setPropertyValue("myblob2_blob", blob);
        session.save();
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        ManagedBlob managedBlob = (ManagedBlob) session.getDocument(doc.getRef()).getPropertyValue("myblob2_blob");
        String key = managedBlob.getKey();

        assertTrue(getReferencedBlobKeys().contains(key), "blob key not referenced: " + key);
    }

}
