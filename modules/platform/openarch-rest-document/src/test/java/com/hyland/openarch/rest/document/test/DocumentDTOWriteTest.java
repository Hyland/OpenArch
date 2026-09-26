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
package com.hyland.openarch.rest.document.test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSGOV_RETAIN_UNTIL;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSVER_CREATED;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSVER_DESCRIPTION;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSVER_TITLE;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_CREATED;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_DESCRIPTION;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_LIFECYCLE_STATE;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_LOCK_CREATED;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_MODIFIED;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_TITLE;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.READ;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.READ_VERSION;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FILE;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FOLDER;
import static org.skyscreamer.jsonassert.JSONCompareMode.STRICT;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import com.hyland.openarch.async.tasks.TaskService;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.Blobs;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.DocumentRef;
import com.hyland.openarch.ecm.core.api.security.ACE;
import com.hyland.openarch.ecm.core.api.security.ACL;
import com.hyland.openarch.ecm.core.test.CoreFeature;
import com.hyland.openarch.ecm.platform.test.UserManagerMockFeature;
import com.hyland.openarch.ecm.platform.test.UserManagerMockTestConfiguration;
import com.hyland.openarch.rest.document.DocumentDTO;
import com.hyland.openarch.rest.document.DocumentMapper;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.mockito.MockitoFeature;
import com.hyland.openarch.runtime.mockito.RuntimeInject;
import com.hyland.openarch.runtime.springboot.junit.RuntimeSpringBootTest;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.transaction.TransactionHelper;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.ValueMatcher;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.skyscreamer.jsonassert.comparator.JSONComparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Features({ CoreFeature.class, UserManagerMockFeature.class, MockitoFeature.class })
@Deploy("org.nuxeo.springboot.rest.document")
@Deploy("org.nuxeo.springboot.rest.document.test:OSGI-INF/test-write-types-contrib.xml")
@Import(UserManagerMockTestConfiguration.class)
@SpringBootTest(classes = { DocumentMapper.class, DocumentAPITestConfiguration.class })
public class DocumentDTOWriteTest extends RuntimeSpringBootTest {

    private static final String MY_FILE = "MyFile";

    private static final String MY_GOVERNABLE_FILE = "MyGovernableFile";

    private static final ValueMatcher<Object> NON_NULL_MATCHER = (o1, o2) -> o1 != null && o2 != null;

    private static final JSONComparator DOC_COMPARATOR = new CustomComparator(STRICT,
            new Customization(SYS_CREATED, NON_NULL_MATCHER), //
            new Customization(SYS_MODIFIED, NON_NULL_MATCHER), //
            new Customization(SYSVER_CREATED, NON_NULL_MATCHER), //
            new Customization(SYS_LOCK_CREATED, NON_NULL_MATCHER),
            new Customization(SYSGOV_RETAIN_UNTIL, NON_NULL_MATCHER));

    @RuntimeInject
    CoreSession session;

    @Autowired
    DocumentMapper documentMapper;

    private void setLifecycleState(DocumentModel doc, String state) {
        doc.setPropertyValue(SYS_LIFECYCLE_STATE, state);
    }

    private void doTestWrite(String type, boolean withEmptyLists, UnaryOperator<DocumentModel> func, String expected)
            throws Exception {
        // create a document under some folders
        DocumentModel foo = session.createDocumentModel("/", "foo", SYS_FOLDER);
        foo.create();

        DocumentModel bar = session.createDocumentModel("/foo", "bar", SYS_FOLDER);
        ACL acl = bar.getLocalACL();
        acl.add(ACE.builder().user("aliceId").permission(READ_VERSION).build());
        bar.setACL(acl, true);
        bar.create();

        DocumentModel doc = session.createDocumentModel("/foo/bar", "file", type);
        doc.setPropertyValue(SYS_TITLE, "Some file");
        doc.setPropertyValue(SYS_DESCRIPTION, "My description");
        doc.setPropertyValue(SYSFILE_BLOB, Blobs.createBlob("hello", "text/plain", "UTF-8", "hello.txt"));
        doc.create();
        acl = doc.getLocalACL();
        acl.add(ACE.builder().user("bobId").permission(READ).build());
        doc.setACL(acl, true);
        setLifecycleState(doc, "draft");
        doc.setLock();
        // additional stuff passed by caller
        doc = func.apply(doc);

        session.save();
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();
        TaskService asyncTasksService = Framework.getService(TaskService.class);
        assertTrue(asyncTasksService.awaitCompletion(Duration.ofSeconds(20)));
        doc.refresh();

        DocumentDTO docDTO = documentMapper.documentModelToDocumentDTO(doc, withEmptyLists);
        String actual = mapper.writeValueAsString(docDTO);

        expected = expected.replace("{DOCID}", doc.getId()) //
                           .replace("{PARENTID}", doc.getParentRef().toString())
                           .replace("{ANCESTORIDS}", String.join("\", \"", doc.getAncestorIds()))
                           .replace("{NAME}", doc.getName());
        JSONAssert.assertEquals(actual, expected, actual, DOC_COMPARATOR);
    }

    @Test
    @Deploy("org.nuxeo.springboot.rest.document.test:OSGI-INF/test-retention-blobprovider-config.xml")
    public void testGovernable() throws Exception {
        doTestWrite(MY_GOVERNABLE_FILE, false, doc -> {
            session.setRecord(doc.getRef(), true);
            Instant twoSeconds = Instant.now().plusSeconds(2);
            session.setRetainUntil(doc.getRef(), twoSeconds);
            // not setting a legal hold to showcase that we output "sysgov_hasLegalHold": false
            return doc;
        }, """
                {
                  "sys_id": "{DOCID}",
                  "sys_parentId": "{PARENTID}",
                  "sys_ancestorIds": ["{ANCESTORIDS}"],
                  "sys_changeToken": "1-0",
                  "sys_mixinTypes": [
                    "SysFilish",
                    "SysGovernable"
                  ],
                  "sys_acl": [
                    {
                      "granted": true,
                      "permission": "Read",
                      "status": "EFFECTIVE",
                      "user": {"id": "bobId"}
                    }
                  ],
                  "sys_effectiveAcl": [
                    {
                      "granted": true,
                      "permission": "Read",
                      "status": "EFFECTIVE",
                      "user": {"id": "bobId"}
                    },
                    {
                      "granted": true,
                      "permission": "ReadVersion",
                      "status": "EFFECTIVE",
                      "user": {"id": "aliceId"}
                    }
                  ],
                  "sys_effectivePermissions": [
                    "CreateChild",
                    "CreateVersion",
                    "DeleteChild",
                    "ManageLock",
                    "ManageRetention",
                    "ManageSecurity",
                    "Read",
                    "ReadVersion",
                    "WriteVersion"
                  ],
                  "sys_name": "file",
                  "sys_parentPath": "/foo/bar",
                  "sys_path": "/foo/bar/file",
                  "sys_primaryType": "MyGovernableFile",
                  "sys_racl": ["u:bobId"],
                  "sys_repository": "test",
                  "sys_lifecycleState": "draft",
                  "sys_lockCreated": "2021-10-24T00:00:00.000Z",
                  "sys_lockOwner": {
                "email" : "test.user@hyland.com",
                "firstName" : "Test",
                "id" : "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                "lastName" : "User",
                "properties" : {
                  "YOJ" : "2013",
                  "empId" : "E443336-3675",
                  "favouriteColor" : "beige"
                  }},
                  "sys_isFolderish": false,
                  "sysgov_hasLegalHold": false,
                  "sysgov_isRecord": true,
                  "sysgov_retainUntil": "2021-10-24T00:00:02.000Z",
                  "sys_pathDepth": 4,
                            "sys_contributors": [{
                "email" : "test.user@hyland.com",
                "firstName" : "Test",
                "id" : "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                "lastName" : "User",
                "properties" : {
                  "YOJ" : "2013",
                  "empId" : "E443336-3675",
                  "favouriteColor" : "beige"
                  }}],
                  "sys_created": "2021-10-24T00:00:00.000Z",
                            "sys_creator": {
                "email" : "test.user@hyland.com",
                "firstName" : "Test",
                "id" : "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                "lastName" : "User",
                "properties" : {
                  "YOJ" : "2013",
                  "empId" : "E443336-3675",
                  "favouriteColor" : "beige"
                  }},
                  "sys_description": "My description",
                            "sys_lastContributor": {
                "email" : "test.user@hyland.com",
                "firstName" : "Test",
                "id" : "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                "lastName" : "User",
                "properties" : {
                  "YOJ" : "2013",
                  "empId" : "E443336-3675",
                  "favouriteColor" : "beige"
                  }},
                  "sys_modified": "2021-10-24T00:00:00.000Z",
                  "sys_title": "Some file",
                  "sysfile_blob": {
                    "key": "records:{DOCID}",
                    "filename": "hello.txt",
                    "mimeType": "text/plain",
                    "encoding": "UTF-8",
                    "digest": "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                    "length": 5
                  }
                }
                """);
    }

    @Test
    public void testGovernableForNonRecord() throws Exception {
        // not setting as record to showcase that we output "sysgov_isRecord": false but filtering out null sysgov_retainUntil
        doTestWrite(MY_GOVERNABLE_FILE, false, doc -> doc, """
                {
                  "sys_id": "{DOCID}",
                  "sys_parentId": "{PARENTID}",
                  "sys_ancestorIds": ["{ANCESTORIDS}"],
                  "sys_changeToken": "1-0",
                  "sys_mixinTypes": [
                    "SysFilish",
                    "SysGovernable"
                  ],
                  "sys_acl": [
                    {
                      "granted": true,
                      "permission": "Read",
                      "status": "EFFECTIVE",
                      "user": {"id": "bobId"}
                    }
                  ],
                  "sys_effectiveAcl": [
                    {
                      "granted": true,
                      "permission": "Read",
                      "status": "EFFECTIVE",
                      "user": {"id": "bobId"}
                    },
                    {
                      "granted": true,
                      "permission": "ReadVersion",
                      "status": "EFFECTIVE",
                      "user": {"id": "aliceId"}
                    }
                  ],
                  "sys_effectivePermissions": [
                    "CreateChild",
                    "CreateVersion",
                    "Delete",
                    "DeleteChild",
                    "ManageLock",
                    "ManageRetention",
                    "ManageSecurity",
                    "Read",
                    "ReadVersion",
                    "ReadWrite",
                    "Write",
                    "WriteVersion"
                  ],
                  "sys_name": "file",
                  "sys_parentPath": "/foo/bar",
                  "sys_path": "/foo/bar/file",
                  "sys_primaryType": "MyGovernableFile",
                  "sys_racl": ["u:bobId"],
                  "sys_repository": "test",
                  "sys_lifecycleState": "draft",
                  "sys_lockCreated": "2021-10-24T00:00:00.000Z",
                            "sys_lockOwner": {
                "email" : "test.user@hyland.com",
                "firstName" : "Test",
                "id" : "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                "lastName" : "User",
                "properties" : {
                  "YOJ" : "2013",
                  "empId" : "E443336-3675",
                  "favouriteColor" : "beige"
                  }},
                  "sys_isFolderish": false,
                  "sysgov_hasLegalHold": false,
                  "sysgov_isRecord": false,
                  "sys_pathDepth": 4,
                            "sys_contributors": [{
                "email" : "test.user@hyland.com",
                "firstName" : "Test",
                "id" : "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                "lastName" : "User",
                "properties" : {
                  "YOJ" : "2013",
                  "empId" : "E443336-3675",
                  "favouriteColor" : "beige"
                  }}],
                  "sys_created": "2021-10-24T00:00:00.000Z",
                            "sys_creator": {
                "email" : "test.user@hyland.com",
                "firstName" : "Test",
                "id" : "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                "lastName" : "User",
                "properties" : {
                  "YOJ" : "2013",
                  "empId" : "E443336-3675",
                  "favouriteColor" : "beige"
                  }},
                  "sys_description": "My description",
                            "sys_lastContributor": {
                "email" : "test.user@hyland.com",
                "firstName" : "Test",
                "id" : "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                "lastName" : "User",
                "properties" : {
                  "YOJ" : "2013",
                  "empId" : "E443336-3675",
                  "favouriteColor" : "beige"
                  }},
                  "sys_modified": "2021-10-24T00:00:00.000Z",
                  "sys_title": "Some file",
                  "sysfile_blob": {
                    "key": "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                    "filename": "hello.txt",
                    "mimeType": "text/plain",
                    "encoding": "UTF-8",
                    "digest": "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                    "length": 5
                  }
                }
                """);
    }

    @Test
    public void testNonVersionable() throws Exception {
        doTestWrite(SYS_FILE, false, doc -> doc, """
                {
                  "sys_id": "{DOCID}",
                  "sys_parentId": "{PARENTID}",
                  "sys_ancestorIds": [
                    "{ANCESTORIDS}"
                  ],
                  "sys_changeToken": "1-0",
                  "sys_mixinTypes": [
                    "SysFilish"
                  ],
                  "sys_acl": [
                    {
                      "granted": true,
                      "permission": "Read",
                      "status": "EFFECTIVE",
                      "user": {
                        "id": "bobId"
                      }
                    }
                  ],
                  "sys_effectiveAcl": [
                    {
                      "granted": true,
                      "permission": "Read",
                      "status": "EFFECTIVE",
                      "user": {
                        "id": "bobId"
                      }
                    },
                    {
                      "granted": true,
                      "permission": "ReadVersion",
                      "status": "EFFECTIVE",
                      "user": {
                        "id": "aliceId"
                      }
                    }
                  ],
                  "sys_effectivePermissions": [
                    "CreateChild",
                    "CreateVersion",
                    "Delete",
                    "DeleteChild",
                    "ManageLock",
                    "ManageRetention",
                    "ManageSecurity",
                    "Read",
                    "ReadVersion",
                    "ReadWrite",
                    "Write",
                    "WriteVersion"
                  ],
                  "sys_name": "file",
                  "sys_parentPath": "/foo/bar",
                  "sys_path": "/foo/bar/file",
                  "sys_primaryType": "SysFile",
                  "sys_racl": ["u:bobId"],
                  "sys_repository": "test",
                  "sys_isFolderish": false,
                  "sys_pathDepth": 4,
                  "sys_contributors": [
                    {
                      "email": "test.user@hyland.com",
                      "firstName": "Test",
                      "id": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                      "lastName": "User",
                      "properties": {
                        "YOJ": "2013",
                        "empId": "E443336-3675",
                        "favouriteColor": "beige"
                      }
                    }
                  ],
                  "sys_created": "2021-10-24T00:00:00.000Z",
                  "sys_creator": {
                    "email": "test.user@hyland.com",
                    "firstName": "Test",
                    "id": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                    "lastName": "User",
                    "properties": {
                      "YOJ": "2013",
                      "empId": "E443336-3675",
                      "favouriteColor": "beige"
                    }
                  },
                  "sys_description": "My description",
                  "sys_lastContributor": {
                    "email": "test.user@hyland.com",
                    "firstName": "Test",
                    "id": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                    "lastName": "User",
                    "properties": {
                      "YOJ": "2013",
                      "empId": "E443336-3675",
                      "favouriteColor": "beige"
                    }
                  },
                  "sys_modified": "2021-10-24T00:00:00.000Z",
                  "sys_title": "Some file",
                  "sys_lifecycleState": "draft",
                  "sys_lockCreated": "2021-10-24T00:00:00.000Z",
                  "sys_lockOwner": {
                    "email": "test.user@hyland.com",
                    "firstName": "Test",
                    "id": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                    "lastName": "User",
                    "properties": {
                      "YOJ": "2013",
                      "empId": "E443336-3675",
                      "favouriteColor": "beige"
                    }
                  },
                  "sysfile_blob": {
                    "key": "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                    "filename": "hello.txt",
                    "mimeType": "text/plain",
                    "encoding": "UTF-8",
                    "digest": "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                    "length": 5
                  }
                }
                """);
    }

    @Test
    public void testWorkingCopyCheckedIn() throws Exception {
        doTestWrite(MY_FILE, false, doc -> {
            doc.checkIn();
            return doc;
        }, """
                    {
                  "sys_id": "{DOCID}",
                  "sys_parentId": "{PARENTID}",
                  "sys_ancestorIds": [
                    "{ANCESTORIDS}"
                  ],
                  "sys_changeToken": "2-0",
                  "sys_mixinTypes": [
                    "SysFilish",
                    "SysVersionable"
                  ],
                  "sys_acl": [
                    {
                      "granted": true,
                      "permission": "Read",
                      "status": "EFFECTIVE",
                      "user": {
                        "id": "bobId"
                      }
                    }
                  ],
                  "sys_effectiveAcl": [
                    {
                      "granted": true,
                      "permission": "Read",
                      "status": "EFFECTIVE",
                      "user": {
                        "id": "bobId"
                      }
                    },
                    {
                      "granted": true,
                      "permission": "ReadVersion",
                      "status": "EFFECTIVE",
                      "user": {
                        "id": "aliceId"
                      }
                    }
                  ],
                  "sys_effectivePermissions": [
                    "CreateChild",
                    "CreateVersion",
                    "Delete",
                    "DeleteChild",
                    "ManageLock",
                    "ManageRetention",
                    "ManageSecurity",
                    "Read",
                    "ReadVersion",
                    "ReadWrite",
                    "Write",
                    "WriteVersion"
                  ],
                  "sys_name": "file",
                  "sys_parentPath": "/foo/bar",
                  "sys_path": "/foo/bar/file",
                  "sys_primaryType": "MyFile",
                  "sys_racl": ["u:bobId"],
                  "sys_repository": "test",
                  "sys_isFolderish": false,
                  "sys_pathDepth": 4,
                  "sys_contributors": [
                    {
                      "email": "test.user@hyland.com",
                      "firstName": "Test",
                      "id": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                      "lastName": "User",
                      "properties": {
                        "YOJ": "2013",
                        "empId": "E443336-3675",
                        "favouriteColor": "beige"
                      }
                    }
                  ],
                  "sys_created": "2021-10-24T00:00:00.000Z",
                  "sys_creator": {
                    "email": "test.user@hyland.com",
                    "firstName": "Test",
                    "id": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                    "lastName": "User",
                    "properties": {
                      "YOJ": "2013",
                      "empId": "E443336-3675",
                      "favouriteColor": "beige"
                    }
                  },
                  "sys_description": "My description",
                  "sys_lastContributor": {
                    "email": "test.user@hyland.com",
                    "firstName": "Test",
                    "id": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                    "lastName": "User",
                    "properties": {
                      "YOJ": "2013",
                      "empId": "E443336-3675",
                      "favouriteColor": "beige"
                    }
                  },
                  "sys_modified": "2021-10-24T00:00:00.000Z",
                  "sys_title": "Some file",
                  "sys_lifecycleState": "draft",
                  "sys_lockCreated": "2021-10-24T00:00:00.000Z",
                  "sys_lockOwner": {
                    "email": "test.user@hyland.com",
                    "firstName": "Test",
                    "id": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                    "lastName": "User",
                    "properties": {
                      "YOJ": "2013",
                      "empId": "E443336-3675",
                      "favouriteColor": "beige"
                    }
                  },
                  "sysver_isCheckedIn": true,
                  "sysver_minorVersion": 0,
                  "sysver_isVersion": false,
                  "sysver_version": 1,
                  "sysfile_blob": {
                    "key": "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                    "filename": "hello.txt",
                    "mimeType": "text/plain",
                    "encoding": "UTF-8",
                    "digest": "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                    "length": 5
                  }
                }
                """);
    }

    @Test
    public void testWorkingCopyCheckedOut() throws Exception {
        doTestWrite(MY_FILE, false, doc -> {
            doc.checkIn();
            doc.checkOut();
            return doc;
        }, """
                    {
                  "sys_id": "{DOCID}",
                  "sys_parentId": "{PARENTID}",
                  "sys_ancestorIds": [
                    "{ANCESTORIDS}"
                  ],
                  "sys_changeToken": "3-0",
                  "sys_mixinTypes": [
                    "SysFilish",
                    "SysVersionable"
                  ],
                  "sys_acl": [
                    {
                      "granted": true,
                      "permission": "Read",
                      "status": "EFFECTIVE",
                      "user": {
                        "id": "bobId"
                      }
                    }
                  ],
                  "sys_effectiveAcl": [
                    {
                      "granted": true,
                      "permission": "Read",
                      "status": "EFFECTIVE",
                      "user": {
                        "id": "bobId"
                      }
                    },
                    {
                      "granted": true,
                      "permission": "ReadVersion",
                      "status": "EFFECTIVE",
                      "user": {
                        "id": "aliceId"
                      }
                    }
                  ],
                  "sys_effectivePermissions": [
                    "CreateChild",
                    "CreateVersion",
                    "Delete",
                    "DeleteChild",
                    "ManageLock",
                    "ManageRetention",
                    "ManageSecurity",
                    "Read",
                    "ReadVersion",
                    "ReadWrite",
                    "Write",
                    "WriteVersion"
                  ],
                  "sys_name": "file",
                  "sys_parentPath": "/foo/bar",
                  "sys_path": "/foo/bar/file",
                  "sys_primaryType": "MyFile",
                  "sys_racl": ["u:bobId"],
                  "sys_repository": "test",
                  "sys_isFolderish": false,
                  "sys_pathDepth": 4,
                  "sys_contributors": [
                    {
                      "email": "test.user@hyland.com",
                      "firstName": "Test",
                      "id": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                      "lastName": "User",
                      "properties": {
                        "YOJ": "2013",
                        "empId": "E443336-3675",
                        "favouriteColor": "beige"
                      }
                    }
                  ],
                  "sys_created": "2021-10-24T00:00:00.000Z",
                  "sys_creator": {
                    "email": "test.user@hyland.com",
                    "firstName": "Test",
                    "id": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                    "lastName": "User",
                    "properties": {
                      "YOJ": "2013",
                      "empId": "E443336-3675",
                      "favouriteColor": "beige"
                    }
                  },
                  "sys_description": "My description",
                  "sys_lastContributor": {
                    "email": "test.user@hyland.com",
                    "firstName": "Test",
                    "id": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                    "lastName": "User",
                    "properties": {
                      "YOJ": "2013",
                      "empId": "E443336-3675",
                      "favouriteColor": "beige"
                    }
                  },
                  "sys_modified": "2021-10-24T00:00:00.000Z",
                  "sys_title": "Some file",
                  "sys_lifecycleState": "draft",
                  "sys_lockCreated": "2021-10-24T00:00:00.000Z",
                  "sys_lockOwner": {
                    "email": "test.user@hyland.com",
                    "firstName": "Test",
                    "id": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                    "lastName": "User",
                    "properties": {
                      "YOJ": "2013",
                      "empId": "E443336-3675",
                      "favouriteColor": "beige"
                    }
                  },
                  "sysver_isCheckedIn": false,
                  "sysver_minorVersion": 0,
                  "sysver_isVersion": false,
                  "sysver_version": 1,
                  "sysfile_blob": {
                    "key": "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                    "filename": "hello.txt",
                    "mimeType": "text/plain",
                    "encoding": "UTF-8",
                    "digest": "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                    "length": 5
                  }
                }
                """);
    }

    @Test
    public void testVersion() throws Exception {
        doTestWrite(MY_FILE, false, doc -> {
            DocumentRef verRef = doc.checkIn();
            DocumentModel ver = doc.getCoreSession().getDocument(verRef);
            ver.setPropertyValue(SYSVER_TITLE, "version title");
            ver.setPropertyValue(SYSVER_DESCRIPTION, "version descr");
            return ver;
        }, """
                {
                  "sys_id": "{DOCID}",
                  "sys_parentId": "{PARENTID}",
                  "sys_ancestorIds": [
                    "{ANCESTORIDS}"
                  ],
                  "sys_changeToken": "2-0",
                  "sys_mixinTypes": [
                    "SysFilish",
                    "SysVersionable"
                  ],
                  "sys_effectiveAcl": [
                    {
                      "granted": true,
                      "permission": "Read",
                      "status": "EFFECTIVE",
                      "user": {
                        "id": "bobId"
                      }
                    },
                    {
                      "granted": true,
                      "permission": "ReadVersion",
                      "status": "EFFECTIVE",
                      "user": {
                        "id": "aliceId"
                      }
                    },
                    {
                      "granted": true,
                      "permission": "Read",
                      "status": "EFFECTIVE",
                      "user": {
                        "id": "aliceId"
                      }
                    }
                  ],
                  "sys_effectivePermissions": [
                    "CreateChild",
                    "CreateVersion",
                    "Delete",
                    "DeleteChild",
                    "ManageLock",
                    "ManageRetention",
                    "ManageSecurity",
                    "Read",
                    "ReadVersion",
                    "ReadWrite",
                    "Write",
                    "WriteVersion"
                  ],
                  "sys_name": "{NAME}",
                  "sys_parentPath": "/foo/bar/file",
                  "sys_path": "/foo/bar/file/{NAME}",
                  "sys_primaryType": "MyFile",
                  "sys_racl": ["u:aliceId", "u:bobId"],
                  "sys_repository": "test",
                  "sys_isFolderish": false,
                  "sys_pathDepth": 5,
                  "sys_contributors": [
                    {
                      "email": "test.user@hyland.com",
                      "firstName": "Test",
                      "id": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                      "lastName": "User",
                      "properties": {
                        "YOJ": "2013",
                        "empId": "E443336-3675",
                        "favouriteColor": "beige"
                      }
                    }
                  ],
                  "sys_created": "2021-10-24T00:00:00.000Z",
                  "sys_creator": {
                    "email": "test.user@hyland.com",
                    "firstName": "Test",
                    "id": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                    "lastName": "User",
                    "properties": {
                      "YOJ": "2013",
                      "empId": "E443336-3675",
                      "favouriteColor": "beige"
                    }
                  },
                  "sys_description": "My description",
                  "sys_lastContributor": {
                    "email": "test.user@hyland.com",
                    "firstName": "Test",
                    "id": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                    "lastName": "User",
                    "properties": {
                      "YOJ": "2013",
                      "empId": "E443336-3675",
                      "favouriteColor": "beige"
                    }
                  },
                  "sys_modified": "2021-10-24T00:00:00.000Z",
                  "sys_title": "Some file",
                  "sys_lifecycleState": "draft",
                  "sys_lockCreated": "2021-10-24T00:00:00.000Z",
                  "sys_lockOwner": {
                    "email": "test.user@hyland.com",
                    "firstName": "Test",
                    "id": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                    "lastName": "User",
                    "properties": {
                      "YOJ": "2013",
                      "empId": "E443336-3675",
                      "favouriteColor": "beige"
                    }
                  },
                  "sysver_created": "2021-10-24T00:00:00.000Z",
                  "sysver_creator": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                  "sysver_description": "version descr",
                  "sysver_isCheckedIn": false,
                  "sysver_isVersion": true,
                  "sysver_minorVersion": 0,
                  "sysver_title": "version title",
                  "sysver_version": 1,
                  "sysfile_blob": {
                    "key": "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                    "filename": "hello.txt",
                    "mimeType": "text/plain",
                    "encoding": "UTF-8",
                    "digest": "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                    "length": 5
                  }
                }
                """);
    }

    @Test
    public void testAny() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("a", "aaa");
        map.put("b", 1); // int
        map.put("c", 2L); // long
        map.put("d", 3.0F); // float
        map.put("e", 4.0D); // double
        map.put("f", true);
        map.put("g", Instant.parse("2001-02-03T04:05:06.007Z"));
        map.put("h", new BigDecimal("1234567890.12345678901234567890"));
        map.put("i", List.of(1, 2, 3)); // list
        map.put("j", new Object[] { 4, 5, 6 }); // array
        map.put("k", Map.of("foo", "bar", "baz", "moo")); // simple map
        map.put("l", List.<Object> of(1, "foo", false, 2.0)); // heterogenous list
        map.put("m", List.of(1, List.of(2, 3), 4)); // list in list
        map.put("n", Map.of("x", List.of("foo", 1, Map.of("u", false), List.of(5, 6)))); // complex
        doTestWrite(MY_FILE, false, doc -> {
            doc.setPropertyValue("any_obj", map);
            return doc;
        }, """
                {
                  "sys_id": "{DOCID}",
                  "sys_parentId": "{PARENTID}",
                  "sys_ancestorIds": ["{ANCESTORIDS}"],
                  "sys_changeToken": "1-0",
                  "sys_mixinTypes": [
                    "SysFilish",
                    "SysVersionable"
                  ],
                  "sys_acl": [
                    {
                      "granted": true,
                      "permission": "Read",
                      "status": "EFFECTIVE",
                      "user": {"id": "bobId"}
                    }
                  ],
                  "sys_effectiveAcl": [
                    {
                      "granted": true,
                      "permission": "Read",
                      "status": "EFFECTIVE",
                      "user": {"id": "bobId"}
                    },
                    {
                      "granted": true,
                      "permission": "ReadVersion",
                      "status": "EFFECTIVE",
                      "user": {"id": "aliceId"}
                    }
                  ],
                  "sys_effectivePermissions": [
                    "CreateChild",
                    "CreateVersion",
                    "Delete",
                    "DeleteChild",
                    "ManageLock",
                    "ManageRetention",
                    "ManageSecurity",
                    "Read",
                    "ReadVersion",
                    "ReadWrite",
                    "Write",
                    "WriteVersion"
                  ],
                  "sys_name": "file",
                  "sys_parentPath": "/foo/bar",
                  "sys_path": "/foo/bar/file",
                  "sys_primaryType": "MyFile",
                  "sys_racl": ["u:bobId"],
                  "sys_repository": "test",
                  "sys_isFolderish": false,
                  "sys_pathDepth": 4,
                  "sys_contributors": [
                    {
                      "email": "test.user@hyland.com",
                      "firstName": "Test",
                      "id": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                      "lastName": "User",
                      "properties": {
                        "YOJ": "2013",
                        "empId": "E443336-3675",
                        "favouriteColor": "beige"
                      }
                    }
                  ],
                  "sys_created": "2021-10-24T00:00:00.000Z",
                  "sys_creator": {
                    "email": "test.user@hyland.com",
                    "firstName": "Test",
                    "id": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                    "lastName": "User",
                    "properties": {
                      "YOJ": "2013",
                      "empId": "E443336-3675",
                      "favouriteColor": "beige"
                    }
                  },
                  "sys_description": "My description",
                  "sys_lastContributor": {
                    "email": "test.user@hyland.com",
                    "firstName": "Test",
                    "id": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                    "lastName": "User",
                    "properties": {
                      "YOJ": "2013",
                      "empId": "E443336-3675",
                      "favouriteColor": "beige"
                    }
                  },
                  "sys_modified": "2021-10-24T00:00:00.000Z",
                  "sys_title": "Some file",
                  "sys_lifecycleState": "draft",
                  "sys_lockCreated": "2021-10-24T00:00:00.000Z",
                  "sys_lockOwner": {
                    "email" : "test.user@hyland.com",
                    "firstName" : "Test",
                    "id" : "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                    "lastName" : "User",
                    "properties" : {
                      "YOJ" : "2013",
                      "empId" : "E443336-3675",
                      "favouriteColor" : "beige"
                      }},
                  "sysver_isCheckedIn": false,
                  "sysver_isVersion": false,
                  "any_obj": {
                    "a": "aaa",
                    "b": 1,
                    "c": 2,
                    "d": 3.0,
                    "e": 4.0,
                    "f": true,
                    "g": "2001-02-03T04:05:06.007Z",
                    "h": 1234567890.12345678901234567890,
                    "i": [1, 2, 3],
                    "j": [4, 5, 6],
                    "k": {
                      "baz": "moo",
                      "foo": "bar"
                    },
                    "l": [1, "foo", false, 2.0],
                    "m": [1, [2, 3], 4],
                    "n": {
                      "x": ["foo", 1, { "u": false }, [5, 6]]
                    }
                  },
                  "sysfile_blob": {
                    "key": "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                    "filename": "hello.txt",
                    "mimeType": "text/plain",
                    "encoding": "UTF-8",
                    "digest": "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                    "length": 5
                  }
                }
                """);
    }

    @Test
    public void testWithEmptyLists() throws Exception {
        // true parameter is passed to require explicit empty lists instead of skipping properties
        doTestWrite(SYS_FILE, true, doc -> {
            // bare bones document
            doc = session.createDocumentModel("/", "file", SYS_FILE);
            doc.create();
            return doc;
        }, """
                {
                  "sys_id": "{DOCID}",
                  "sys_parentId": "{PARENTID}",
                  "sys_ancestorIds": [
                    "{ANCESTORIDS}"
                  ],
                  "sys_changeToken": "0-0",
                  "sys_mixinTypes": [
                    "SysFilish"
                  ],
                  "sys_acl": [],
                  "sys_effectiveAcl": [],
                  "sys_effectivePermissions": [
                    "CreateChild",
                    "CreateVersion",
                    "Delete",
                    "DeleteChild",
                    "ManageLock",
                    "ManageRetention",
                    "ManageSecurity",
                    "Read",
                    "ReadVersion",
                    "ReadWrite",
                    "Write",
                    "WriteVersion"
                  ],
                  "sys_name": "file",
                  "sys_parentPath": "/",
                  "sys_path": "/file",
                  "sys_primaryType": "SysFile",
                  "sys_racl": [],
                  "sys_repository": "test",
                  "sys_isFolderish": false,
                  "sys_pathDepth": 2,
                  "sys_contributors": [
                    {
                      "email": "test.user@hyland.com",
                      "firstName": "Test",
                      "id": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                      "lastName": "User",
                      "properties": {
                        "YOJ": "2013",
                        "empId": "E443336-3675",
                        "favouriteColor": "beige"
                      }
                    }
                  ],
                  "sys_created": "2021-10-24T00:00:00.000Z",
                  "sys_creator": {
                    "email": "test.user@hyland.com",
                    "firstName": "Test",
                    "id": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                    "lastName": "User",
                    "properties": {
                      "YOJ": "2013",
                      "empId": "E443336-3675",
                      "favouriteColor": "beige"
                    }
                  },
                  "sys_lastContributor": {
                    "email": "test.user@hyland.com",
                    "firstName": "Test",
                    "id": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                    "lastName": "User",
                    "properties": {
                      "YOJ": "2013",
                      "empId": "E443336-3675",
                      "favouriteColor": "beige"
                    }
                  },
                  "sys_modified": "2021-10-24T00:00:00.000Z",
                  "sys_title": "file",
                  "sys_secpol": [],
                  "sysfiles_blobs": []
                }
                """);

        doTestWrite(SYS_FILE, true, doc -> doc, """
                    {
                  "sys_id": "{DOCID}",
                  "sys_parentId": "{PARENTID}",
                  "sys_ancestorIds": ["{ANCESTORIDS}"],
                  "sys_changeToken": "1-0",
                  "sys_mixinTypes": [
                    "SysFilish"
                  ],
                  "sys_acl": [
                    {
                      "granted": true,
                      "permission": "Read",
                      "status": "EFFECTIVE",
                      "user": {"id": "bobId"}
                    }
                  ],
                  "sys_effectiveAcl": [
                    {
                      "granted": true,
                      "permission": "Read",
                      "status": "EFFECTIVE",
                      "user": {"id": "bobId"}
                    },
                    {
                      "granted": true,
                      "permission": "ReadVersion",
                      "status": "EFFECTIVE",
                      "user": {"id": "aliceId"}
                    }
                  ],
                  "sys_effectivePermissions": [
                    "CreateChild",
                    "CreateVersion",
                    "Delete",
                    "DeleteChild",
                    "ManageLock",
                    "ManageRetention",
                    "ManageSecurity",
                    "Read",
                    "ReadVersion",
                    "ReadWrite",
                    "Write",
                    "WriteVersion"
                  ],
                  "sys_name": "{NAME}",
                  "sys_parentPath": "/foo/bar",
                  "sys_path": "/foo/bar/{NAME}",
                  "sys_primaryType": "SysFile",
                  "sys_racl": ["u:bobId"],
                  "sys_repository": "test",
                  "sys_isFolderish": false,
                  "sys_pathDepth": 4,
                  "sys_contributors": [
                    {
                      "email": "test.user@hyland.com",
                      "firstName": "Test",
                      "id": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                      "lastName": "User",
                      "properties": {
                        "YOJ": "2013",
                        "empId": "E443336-3675",
                        "favouriteColor": "beige"
                      }
                    }
                  ],
                  "sys_created": "2021-10-24T00:00:00.000Z",
                  "sys_creator": {
                    "email": "test.user@hyland.com",
                    "firstName": "Test",
                    "id": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                    "lastName": "User",
                    "properties": {
                      "YOJ": "2013",
                      "empId": "E443336-3675",
                      "favouriteColor": "beige"
                    }
                  },
                  "sys_description": "My description",
                  "sys_lastContributor": {
                    "email" : "test.user@hyland.com",
                    "firstName" : "Test",
                    "id" : "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                    "lastName" : "User",
                    "properties" : {
                      "YOJ" : "2013",
                      "empId" : "E443336-3675",
                      "favouriteColor" : "beige"
                      }},
                  "sys_modified": "2021-10-24T00:00:00.000Z",
                  "sys_title": "Some file",
                  "sys_lifecycleState": "draft",
                  "sys_lockCreated": "2021-10-24T00:00:00.000Z",
                  "sys_lockOwner": {
                    "email": "test.user@hyland.com",
                    "firstName": "Test",
                    "id": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                    "lastName": "User",
                    "properties": {
                      "YOJ": "2013",
                      "empId": "E443336-3675",
                      "favouriteColor": "beige"
                    }
                  },
                  "sys_secpol": [],
                  "sysfile_blob": {
                    "key": "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                    "filename": "hello.txt",
                    "mimeType": "text/plain",
                    "encoding": "UTF-8",
                    "digest": "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                    "length": 5
                  },
                  "sysfiles_blobs": []
                }
                """);
    }

}
