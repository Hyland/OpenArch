/*
 * (C) Copyright 2025-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_READ_ACL;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.READ;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.impl.ACLImpl;
import org.nuxeo.ecm.core.api.validation.DocumentValidationException;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.SchemaManagerDTO;
import org.nuxeo.ecm.core.schema.TypeService;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.transaction.TransactionHelper;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.Jackson3JsonPatch;

import jakarta.inject.Inject;

@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
class TestDocumentWithOldContentModel {

    private static final String MY_TYPE = "MyType";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    protected CoreSession session;

    @Inject
    public TypeService typeService;

    private String repositoryName;

    @BeforeEach
    void setUp() {
        repositoryName = session.getRepositoryName();
    }

    private void patchSchemaManager(String patch) {
        // read
        SchemaManager schemaManager = typeService.getSchemaManager(repositoryName);
        // patch
        Map<String, Object> map = schemaManager.toMap();
        JsonNode patched = Jackson3JsonPatch.apply(MAPPER.readTree(patch), MAPPER.valueToTree(map));
        map = MAPPER.convertValue(patched, Map.class);
        // save
        typeService.setSchemaManager(repositoryName, new SchemaManagerDTO(map));
        // commit tx so that the existing session acquires the new schema manager
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();
    }

    @SuppressWarnings("unchecked")
    private List<String> getReadAcl(DocumentRef docRef) {
        return (List<String>) session.getDocument(docRef).getPropertyValue(SYS_READ_ACL);
    }

    @Test
    void testOldType() {
        // add custom MyType to content model
        patchSchemaManager("""
                [
                  {
                    "op": "add",
                    "path": "/primaryTypes/MyType",
                    "value": {
                      "extends": "SysFile"
                    }
                  }
                ]
                """);

        // create a doc of type MyType
        var doc = session.createDocumentModel("/", "doc", MY_TYPE);
        doc.create();
        DocumentRef rootRef = doc.getParentRef();
        session.save();

        // remove MyType from content model
        patchSchemaManager("""
                [
                  {
                    "op": "remove",
                    "path": "/primaryTypes/MyType"
                  }
                ]
                """);

        // check document access fails
        DocumentNotFoundException exc = assertThrows(DocumentNotFoundException.class,
                () -> session.getDocument(doc.getRef()));
        assertEquals("Unknown document type: MyType", exc.getMessage());

        // set ACL on root with Read
        ACL acl = new ACLImpl();
        acl.add(ACE.builder().user("bob").permission(READ).build());
        session.getDocument(rootRef).setACL(acl, true);
        // make sure save it doesn't fail on unknown type while recursing to compute sys_racl
        // avoid: DocumentNotFoundException: Unknown document type: MyType
        session.save();

        // if we add back the type MyType
        patchSchemaManager("""
                [
                  {
                    "op": "add",
                    "path": "/primaryTypes/MyType",
                    "value": {
                      "extends": "SysFile"
                    }
                  }
                ]
                """);

        // the doc can be read again and has proper sys_racl
        assertEquals(List.of("u:bob"), getReadAcl(doc.getRef()));
    }

    @Test
    void testOldConstraint() {
        // add custom MyType to content model with no constraint
        patchSchemaManager("""
                [
                  {
                    "op": "add",
                    "path": "/schemas/myschema",
                    "value": {
                      "prefix": "my",
                      "fields": {
                        "my_field": {
                          "extends": "string",
                          "constraints": {
                            "nullable": true
                          }
                        }
                      }
                    }
                  },
                  {
                    "op": "add",
                    "path": "/primaryTypes/MyType",
                    "value": {
                      "extends": "SysFile",
                      "schemas": ["myschema"]
                    }
                  }
                ]
                """);

        // create a doc of type MyType, with null my_field
        var doc = session.createDocumentModel("/", "doc", MY_TYPE);
        doc.create();
        DocumentRef rootRef = doc.getParentRef();
        session.save();

        // add non-nullable constraint to my_field
        patchSchemaManager("""
                [
                  {
                    "op": "replace",
                    "path": "/schemas/myschema/fields/my_field",
                    "value": {
                      "extends": "string",
                      "constraints": {
                        "nullable": false
                      }
                    }
                  }
                ]
                """);

        // check document access works
        doc = session.getDocument(doc.getRef());
        assertEquals(MY_TYPE, doc.getType());

        // but it can't be saved without a value for my_field
        doc.setPropertyValue(SYS_TITLE, "foo");
        DocumentValidationException exc = assertThrows(DocumentValidationException.class, () -> session.save());
        assertEquals("Constraint violation thrown on property my_field: 'label.schema.constraint.violation'",
                exc.getMessage());

        // set ACL on root with Read
        ACL acl = new ACLImpl();
        acl.add(ACE.builder().user("bob").permission(READ).build());
        session.getDocument(rootRef).setACL(acl, true);
        // make sure save doesn't fail on unknown type while recursing to compute sys_racl
        // avoid: DocumentValidationException: Constraint violation thrown on property my_field:
        // 'label.schema.constraint.violation'
        session.save();

        // the doc has proper sys_racl
        assertEquals(List.of("u:bob"), getReadAcl(doc.getRef()));
    }

}
