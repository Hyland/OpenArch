/*
 * (C) Copyright 2014-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.opensearch.test.io;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSGOV_RETAIN_UNTIL;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_CREATED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_DESCRIPTION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSVER_TITLE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_CREATED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_DESCRIPTION;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_IS_TRASHED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_LIFECYCLE_STATE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_LOCK_CREATED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_MODIFIED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_SECURITY_POLICIES;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_TITLE;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.EVERYTHING;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.READ;
import static org.nuxeo.ecm.core.api.security.SecurityConstants.READ_VERSION;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FILE;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FOLDER;
import static org.skyscreamer.jsonassert.JSONCompareMode.STRICT;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.security.SecurityService;
import org.nuxeo.ecm.core.security.config.SecurityLevelSecurityPolicyConfig;
import org.nuxeo.ecm.core.security.config.SecurityPolicyConfig;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.opensearch.io.JsonOpenSearchDocumentWriter;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;
import com.hyland.openarch.runtime.transaction.TransactionHelper;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.jackson3.JacksonJsonpMapper;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.ValueMatcher;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.skyscreamer.jsonassert.comparator.JSONComparator;

import com.hyland.hxpr.async.tasks.TaskService;

import jakarta.inject.Inject;
import jakarta.json.stream.JsonGenerator;
import tools.jackson.databind.json.JsonMapper;

/**
 * @since 5.9.5
 */
@ExtendWith(FeaturesExtension.class)
@Features(CoreFeature.class)
@Deploy("org.nuxeo.opensearch.core")
@Deploy("org.nuxeo.opensearch.core:opensearch-test-contrib.xml")
@Deploy("org.nuxeo.opensearch.core:test-security-policy-types.xml")
public class TestDocumentModelWriter {

    private static final String REPOSITORY = "test";

    private static final String SECURITY_POLICY_NAME = "mypolicy";

    private static final String SECURITY_POLICY_MIXIN = "Clearance";

    private static final String SECURITY_POLICY_USER_ATTRIBUTE = "clearance";

    private static final String MY_FILE = "MyFile";

    private static final ValueMatcher<Object> NON_NULL_MATCHER = (o1, o2) -> o1 != null && o2 != null;

    private static final JSONComparator DOC_COMPARATOR = new CustomComparator(STRICT,
            new Customization(SYS_CREATED, NON_NULL_MATCHER), //
            new Customization(SYS_MODIFIED, NON_NULL_MATCHER), //
            new Customization(SYSVER_CREATED, NON_NULL_MATCHER), //
            new Customization(SYS_LOCK_CREATED, NON_NULL_MATCHER),
            new Customization(SYSGOV_RETAIN_UNTIL, NON_NULL_MATCHER));

    private static final JsonpMapper MAPPER = new JacksonJsonpMapper(JsonMapper.builder().build());

    private static class TestWriter extends JsonOpenSearchDocumentWriter {

        TestWriter(JsonGenerator jg) {
            super(jg, MAPPER);
        }

        void writeTestValue(String name, Object value) {
            writeValue(name, value);
        }
    }

    @Inject
    private CoreSession session;

    private void setLifecycleState(DocumentModel doc, String state) {
        doc.setPropertyValue(SYS_LIFECYCLE_STATE, state);
    }

    @BeforeAll
    public static void beforeClass() {
        // default config
        SecurityService securityService = Framework.getService(SecurityService.class);
        var config = securityService.getSecurityPolicyService(REPOSITORY).getSecurityPolicyConfigMap();

        // add custom security policy config
        SecurityPolicyConfig custom = new SecurityLevelSecurityPolicyConfig(null, Set.of(SYS_FILE), Set.of(EVERYTHING),
                SECURITY_POLICY_MIXIN, SECURITY_POLICY_USER_ATTRIBUTE);
        config.put(SECURITY_POLICY_NAME, custom);

        securityService.setSecurityPolicies(REPOSITORY, config);
    }

    private void doTestWrite(String type, UnaryOperator<DocumentModel> func, String expected) throws Exception {
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
        doc.setPropertyValue("mydc_subjects", List.of("topic1", "topic2"));
        doc.setPropertyValue(SYSFILE_BLOB, Blobs.createBlob("hello", "text/plain", "UTF-8", "hello.txt"));
        doc.create();
        acl = doc.getLocalACL();
        acl.add(ACE.builder().user("bobId").permission(READ).build());
        doc.setACL(acl, true);
        setLifecycleState(doc, "draft");
        doc.setLock();
        doc.setPropertyValue(SYS_SECURITY_POLICIES, List.of(SECURITY_POLICY_NAME));
        // additional stuff passed by caller
        doc = func.apply(doc);

        session.save();
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();
        TaskService asyncTasksService = Framework.getService(TaskService.class);
        assertTrue(asyncTasksService.awaitCompletion(Duration.ofSeconds(20)));
        doc.refresh();

        OutputStream out = new ByteArrayOutputStream();
        try (JsonGenerator jg = MAPPER.jsonProvider().createGenerator(out)) {
            new JsonOpenSearchDocumentWriter(jg, MAPPER).write(doc);
        }
        String actual = out.toString();
        expected = expected.replace("{DOCID}", doc.getId()) //
                           .replace("{PARENTID}", doc.getParentRef().toString())
                           .replace("{ANCESTORIDS}", String.join("\", \"",doc.getAncestorIds()))
                           .replace("{NAME}", doc.getName());
        JSONAssert.assertEquals(actual, expected, actual, DOC_COMPARATOR);
    }

    @Test
    public void testSystemValuePreservesFloatingPoint() throws Exception {
        OutputStream out = new ByteArrayOutputStream();
        try (JsonGenerator jg = MAPPER.jsonProvider().createGenerator(out)) {
            jg.writeStartObject();
            new TestWriter(jg).writeTestValue("value", 1.25);
            jg.writeEnd();
        }
        JSONAssert.assertEquals("{\"value\":1.25}", out.toString(), true);
    }

    @Test
    public void testGovernable() throws Exception {
        doTestWrite(MY_FILE, doc -> {
            session.setRecord(doc.getRef(), true);
            Instant twoSeconds = Instant.now().plusSeconds(2);
            session.setRetainUntil(doc.getRef(), twoSeconds);
            return doc;
            // not setting a legal hold to showcase that we do NOT output "sysgov_hasLegalHold": false for not indexing fields with false value
        }, """
                {
                  "sys_id": "{DOCID}",
                  "sys_parentId": "{PARENTID}",
                  "sys_ancestorIds": ["{ANCESTORIDS}"],
                  "sys_racl": ["u:bobId"],
                  "sys_secpol": ["mypolicy"],
                  "sys_changeToken": "1-0",
                  "sys_mixinTypes": [
                    "Clearance",
                    "SysFilish",
                    "SysGovernable",
                    "SysVersionable"
                  ],
                  "sys_name": "file",
                  "sys_parentPath": "/foo/bar",
                  "sys_path": "/foo/bar/file",
                  "sys_primaryType": "MyFile",
                  "sys_repository": "test",
                  "sys_title": "Some file",
                  "sys_isFolderish": false,
                  "sysgov_isRecord": true,
                  "sysgov_retainUntil": "2021-10-24T00:00:02.000Z",
                  "sys_pathDepth": 4,
                  "sys_contributors": ["ff7b0360-3594-416c-b587-2fa4dd0839ce"],
                  "sys_created": "2021-10-24T00:00:00.000Z",
                  "sys_creator": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                  "sys_description": "My description",
                  "sys_lastContributor": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                  "sys_modified": "2021-10-24T00:00:00.000Z",
                  "sys_title": "Some file",
                  "sys_lifecycleState": "draft",
                  "sys_lockCreated": "2021-10-24T00:00:00.000Z",
                  "sys_lockOwner": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                  "sysfile_blob": {
                    "key": "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                    "filename": "hello.txt",
                    "mimeType": "text/plain",
                    "encoding": "UTF-8",
                    "digest": "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                    "length": 5
                  },
                  "mydc_subjects": ["topic1","topic2"]
                }
                """);
    }

    @Test
    public void testGovernableForNonRecord() throws Exception {
        // not setting as record to showcase that we are filtering out null and false values for not indexing false or null fields
        // even if the document is governable
        doTestWrite(MY_FILE, doc -> doc, """
                {
                  "sys_id": "{DOCID}",
                  "sys_parentId": "{PARENTID}",
                  "sys_ancestorIds": ["{ANCESTORIDS}"],
                  "sys_racl": ["u:bobId"],
                  "sys_secpol": ["mypolicy"],
                  "sys_changeToken": "1-0",
                  "sys_mixinTypes": [
                    "Clearance",
                    "SysFilish",
                    "SysGovernable",
                    "SysVersionable"
                  ],
                  "sys_name": "file",
                  "sys_parentPath": "/foo/bar",
                  "sys_path": "/foo/bar/file",
                  "sys_primaryType": "MyFile",
                  "sys_repository": "test",
                  "sys_title": "Some file",
                  "sys_isFolderish": false,
                  "sys_pathDepth": 4,
                  "sys_contributors": ["ff7b0360-3594-416c-b587-2fa4dd0839ce"],
                  "sys_created": "2021-10-24T00:00:00.000Z",
                  "sys_creator": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                  "sys_description": "My description",
                  "sys_lastContributor": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                  "sys_modified": "2021-10-24T00:00:00.000Z",
                  "sys_title": "Some file",
                  "sys_lifecycleState": "draft",
                  "sys_lockCreated": "2021-10-24T00:00:00.000Z",
                  "sys_lockOwner": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                  "sysfile_blob": {
                    "key": "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                    "filename": "hello.txt",
                    "mimeType": "text/plain",
                    "encoding": "UTF-8",
                    "digest": "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                    "length": 5
                  },
                  "mydc_subjects": ["topic1","topic2"]
                }
                """);
    }

    @Test
    public void testNonVersionable() throws Exception {
        doTestWrite(SYS_FILE, doc -> {
            CoreInstance.doPrivileged(doc.getRepositoryName(), systemSession -> {
                systemSession.getDocument(doc.getRef()).setPropertyValue(SYS_IS_TRASHED, Boolean.TRUE);
            });
            return doc;
        }, """
                {
                  "sys_id": "{DOCID}",
                  "sys_parentId": "{PARENTID}",
                  "sys_ancestorIds": ["{ANCESTORIDS}"],
                  "sys_racl": ["u:bobId"],
                  "sys_secpol": ["mypolicy"],
                  "sys_changeToken": "1-0",
                  "sys_mixinTypes": [
                    "Clearance",
                    "SysFilish"
                  ],
                  "sys_name": "file",
                  "sys_parentPath": "/foo/bar",
                  "sys_path": "/foo/bar/file",
                  "sys_primaryType": "SysFile",
                  "sys_repository": "test",
                  "sys_title": "Some file",
                  "sys_isFolderish": false,
                  "sys_isTrashed": true,
                  "sys_pathDepth": 4,
                  "sys_contributors": ["ff7b0360-3594-416c-b587-2fa4dd0839ce"],
                  "sys_created": "2021-10-24T00:00:00.000Z",
                  "sys_creator": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                  "sys_description": "My description",
                  "sys_lastContributor": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                  "sys_modified": "2021-10-24T00:00:00.000Z",
                  "sys_title": "Some file",
                  "sys_lifecycleState": "draft",
                  "sys_lockCreated": "2021-10-24T00:00:00.000Z",
                  "sys_lockOwner": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                  "sysfile_blob": {
                    "key": "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                    "filename": "hello.txt",
                    "mimeType": "text/plain",
                    "encoding": "UTF-8",
                    "digest": "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                    "length": 5
                  },
                  "mydc_subjects": ["topic1","topic2"]
                }
                """);
    }

    @Test
    public void testWorkingCopyCheckedIn() throws Exception {
        doTestWrite(MY_FILE, doc -> {
            doc.checkIn();
            return doc;
        }, """
                {
                  "sys_id": "{DOCID}",
                  "sys_parentId": "{PARENTID}",
                  "sys_ancestorIds": ["{ANCESTORIDS}"],
                  "sys_racl": ["u:bobId"],
                  "sys_secpol": ["mypolicy"],
                  "sys_changeToken": "2-0",
                  "sys_mixinTypes": [
                    "Clearance",
                    "SysFilish",
                    "SysGovernable",
                    "SysVersionable"
                  ],
                  "sys_name": "file",
                  "sys_parentPath": "/foo/bar",
                  "sys_path": "/foo/bar/file",
                  "sys_primaryType": "MyFile",
                  "sys_repository": "test",
                  "sys_title": "Some file",
                  "sys_isFolderish": false,
                  "sys_pathDepth": 4,
                  "sys_contributors": ["ff7b0360-3594-416c-b587-2fa4dd0839ce"],
                  "sys_created": "2021-10-24T00:00:00.000Z",
                  "sys_creator": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                  "sys_description": "My description",
                  "sys_lastContributor": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                  "sys_modified": "2021-10-24T00:00:00.000Z",
                  "sys_title": "Some file",
                  "sys_lifecycleState": "draft",
                  "sys_lockCreated": "2021-10-24T00:00:00.000Z",
                  "sys_lockOwner": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                  "sysver_isCheckedIn": true,
                  "sysver_minorVersion": 0,
                  "sysver_version": 1,
                  "sysfile_blob": {
                    "key": "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                    "filename": "hello.txt",
                    "mimeType": "text/plain",
                    "encoding": "UTF-8",
                    "digest": "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                    "length": 5
                  },
                  "mydc_subjects": ["topic1","topic2"]
                }
                """);
    }

    @Test
    public void testWorkingCopyCheckedOut() throws Exception {
        doTestWrite(MY_FILE, doc -> {
            doc.checkIn();
            doc.checkOut();
            return doc;
        }, """
                {
                  "sys_id": "{DOCID}",
                  "sys_parentId": "{PARENTID}",
                  "sys_ancestorIds": ["{ANCESTORIDS}"],
                  "sys_racl": ["u:bobId"],
                  "sys_secpol": ["mypolicy"],
                  "sys_changeToken": "3-0",
                  "sys_mixinTypes": [
                    "Clearance",
                    "SysFilish",
                    "SysGovernable",
                    "SysVersionable"
                  ],
                  "sys_name": "file",
                  "sys_parentPath": "/foo/bar",
                  "sys_path": "/foo/bar/file",
                  "sys_primaryType": "MyFile",
                  "sys_repository": "test",
                  "sys_title": "Some file",
                  "sys_isFolderish": false,
                  "sys_pathDepth": 4,
                  "sys_contributors": ["ff7b0360-3594-416c-b587-2fa4dd0839ce"],
                  "sys_created": "2021-10-24T00:00:00.000Z",
                  "sys_creator": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                  "sys_description": "My description",
                  "sys_lastContributor": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                  "sys_modified": "2021-10-24T00:00:00.000Z",
                  "sys_title": "Some file",
                  "sys_lifecycleState": "draft",
                  "sys_lockCreated": "2021-10-24T00:00:00.000Z",
                  "sys_lockOwner": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                  "sysver_minorVersion": 0,
                  "sysver_version": 1,
                  "sysfile_blob": {
                    "key": "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                    "filename": "hello.txt",
                    "mimeType": "text/plain",
                    "encoding": "UTF-8",
                    "digest": "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                    "length": 5
                  },
                  "mydc_subjects": ["topic1","topic2"]
                }
                """);
    }

    @Test
    public void testVersion() throws Exception {
        doTestWrite(MY_FILE, doc -> {
            DocumentRef verRef = doc.checkIn();
            DocumentModel ver = doc.getCoreSession().getDocument(verRef);
            ver.setPropertyValue(SYSVER_TITLE, "version title");
            ver.setPropertyValue(SYSVER_DESCRIPTION, "version descr");
            return ver;
        }, """
                {
                  "sys_id": "{DOCID}",
                  "sys_parentId": "{PARENTID}",
                  "sys_ancestorIds": ["{ANCESTORIDS}"],
                  "sys_racl": ["u:aliceId", "u:bobId"],
                  "sys_secpol": ["mypolicy"],
                  "sys_changeToken": "2-0",
                  "sys_mixinTypes": [
                    "Clearance",
                    "SysFilish",
                    "SysGovernable",
                    "SysVersionable"
                  ],
                  "sys_name": "{NAME}",
                  "sys_parentPath": "/foo/bar/file",
                  "sys_path": "/foo/bar/file/{NAME}",
                  "sys_primaryType": "MyFile",
                  "sys_repository": "test",
                  "sys_title": "Some file",
                  "sys_isFolderish": false,
                  "sys_pathDepth": 5,
                  "sys_contributors": ["ff7b0360-3594-416c-b587-2fa4dd0839ce"],
                  "sys_created": "2021-10-24T00:00:00.000Z",
                  "sys_creator": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                  "sys_description": "My description",
                  "sys_lastContributor": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                  "sys_modified": "2021-10-24T00:00:00.000Z",
                  "sys_title": "Some file",
                  "sys_lifecycleState": "draft",
                  "sys_lockCreated": "2021-10-24T00:00:00.000Z",
                  "sys_lockOwner": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                  "sysver_created": "2021-10-24T00:00:00.000Z",
                  "sysver_creator": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                  "sysver_description": "version descr",
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
                  },
                  "mydc_subjects": ["topic1","topic2"]
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
        doTestWrite(MY_FILE, doc -> {
            doc.setPropertyValue("any_obj", map);
            return doc;
        }, """
                {
                  "sys_id": "{DOCID}",
                  "sys_parentId": "{PARENTID}",
                  "sys_ancestorIds": ["{ANCESTORIDS}"],
                  "sys_racl": ["u:bobId"],
                  "sys_secpol": ["mypolicy"],
                  "sys_changeToken": "1-0",
                  "sys_mixinTypes": [
                    "Clearance",
                    "SysFilish",
                    "SysGovernable",
                    "SysVersionable"
                  ],
                  "sys_name": "file",
                  "sys_parentPath": "/foo/bar",
                  "sys_path": "/foo/bar/file",
                  "sys_primaryType": "MyFile",
                  "sys_repository": "test",
                  "sys_title": "Some file",
                  "sys_isFolderish": false,
                  "sys_pathDepth": 4,
                  "sys_contributors": ["ff7b0360-3594-416c-b587-2fa4dd0839ce"],
                  "sys_created": "2021-10-24T00:00:00.000Z",
                  "sys_creator": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                  "sys_description": "My description",
                  "sys_lastContributor": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
                  "sys_modified": "2021-10-24T00:00:00.000Z",
                  "sys_title": "Some file",
                  "sys_lifecycleState": "draft",
                  "sys_lockCreated": "2021-10-24T00:00:00.000Z",
                  "sys_lockOwner": "ff7b0360-3594-416c-b587-2fa4dd0839ce",
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
                  },
                  "mydc_subjects": ["topic1","topic2"]
                }
                """);
    }

}
