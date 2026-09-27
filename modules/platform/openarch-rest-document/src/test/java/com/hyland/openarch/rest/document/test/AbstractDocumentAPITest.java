/*
 * (C) Copyright 2021-2026 Hyland (https://www.hyland.com/).
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

import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_PATCH_JSON;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.CONFLICT;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.NOT_ACCEPTABLE;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.NO_CONTENT;
import static jakarta.ws.rs.core.Response.Status.OK;
import static jakarta.ws.rs.core.Response.Status.PRECONDITION_FAILED;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static com.hyland.openarch.ecm.core.api.CoreSession.RETAIN_UNTIL_INDETERMINATE_ISO;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSGOV_HAS_LEGAL_HOLD;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSGOV_IS_RECORD;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYSGOV_RETAIN_UNTIL;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ACL;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ID;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_IS_FOLDERISH;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_MIXIN_TYPES;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_NAME;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PATH;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PRIMARY_TYPE;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_TITLE;
import static com.hyland.openarch.ecm.core.api.impl.blob.AbstractBlob.TEXT_PLAIN;
import static com.hyland.openarch.ecm.core.api.impl.blob.AbstractBlob.UTF_8;
import static com.hyland.openarch.ecm.core.api.local.DummyLoginFeature.TEST_ADMIN_USER;
import static com.hyland.openarch.ecm.core.api.local.DummyLoginFeature.TEST_ANONYMOUS_USER;
import static com.hyland.openarch.ecm.core.api.security.SecurityConstants.EVERYTHING;
import static com.hyland.openarch.ecm.core.schema.MixinNames.SYS_FILISH;
import static com.hyland.openarch.ecm.core.schema.MixinNames.SYS_FOLDERISH;
import static com.hyland.openarch.ecm.core.schema.MixinNames.SYS_ORDERABLE;
import static com.hyland.openarch.ecm.core.schema.TypeConstants.SYS_FILE;
import static com.hyland.openarch.rest.document.AbstractDocumentAPI.PATH;
import static com.hyland.openarch.rest.document.test.ContentTestHelper.checkErrorResponse;
import static com.hyland.openarch.rest.document.test.ContentTestHelper.getJsonPath;
import static com.hyland.openarch.rest.document.test.MultiPartTestHelper.getBlobCreationJson;
import static com.hyland.openarch.rest.document.test.MultiPartTestHelper.getBlobCreationJsonWithOneBinary;
import static com.hyland.openarch.rest.document.test.MultiPartTestHelper.getBlobCreationJsonWithThreeBinary;
import static com.hyland.openarch.rest.document.test.MultiPartTestHelper.getBlobCreationJsonWithTwoBinary;
import static com.hyland.openarch.rest.document.test.MultiPartTestHelper.getComplexUploadIdPart;
import static com.hyland.openarch.rest.document.test.MultiPartTestHelper.getInvalidBlobCreationJson;
import static com.hyland.openarch.rest.document.test.MultiPartTestHelper.getListUploadIdPart;
import static com.hyland.openarch.rest.document.test.MultiPartTestHelper.getMainUploadIdPart;
import static com.hyland.openarch.rest.utils.Headers.HXCS_REPOSITORY;
import static com.hyland.openarch.rest.utils.QueryParameters.CONDITION;
import static com.hyland.openarch.rest.utils.QueryParameters.ENFORCE_SYS_NAME;
import static com.hyland.openarch.rest.utils.QueryParameters.IF;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.core.MediaType;
import org.json.JSONException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.ecm.core.api.Blob;
import com.hyland.openarch.ecm.core.api.Blobs;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.DocumentSecurityException;
import com.hyland.openarch.ecm.core.api.IdRef;
import com.hyland.openarch.ecm.core.api.impl.UserPrincipal;
import com.hyland.openarch.ecm.core.api.local.DummyLoginFeature;
import com.hyland.openarch.ecm.core.api.local.WithUser;
import com.hyland.openarch.ecm.core.security.SecurityService;
import com.hyland.openarch.ecm.core.security.config.SecurityLevelSecurityPolicyConfig;
import com.hyland.openarch.ecm.core.security.config.SecurityPolicyConfig;
import com.hyland.openarch.ecm.platform.vocabulary.VocabularyService;
import com.hyland.openarch.ecm.platform.vocabulary.model.CreateVocabulary;
import com.hyland.openarch.rest.upload.Upload;
import com.hyland.openarch.rest.upload.UploadAttachStatus;
import com.hyland.openarch.rest.upload.UploadHandlerCleaner;
import com.hyland.openarch.rest.upload.handler.UploadHandler;
import com.hyland.openarch.rest.utils.Versions;
import com.hyland.openarch.runtime.api.Framework;
import com.hyland.openarch.runtime.api.login.LoginService;
import com.hyland.openarch.runtime.mockito.RuntimeInject;
import com.hyland.openarch.runtime.test.runner.Deploy;
import com.hyland.openarch.runtime.test.runner.Features;
import com.hyland.openarch.runtime.transaction.TransactionHelper;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Unit-test version of the server document api rest testing.
 */
@Features(RestSingleDocumentFeature.class)
@Deploy("org.nuxeo.ecm.platform.vocabularies:OSGI-INF/vocabulary-resolver-contrib.xml")
@Deploy("com.hyland.openarch.springboot.rest.document.test:OSGI-INF/test-custom-facet-contrib.xml")
@Deploy("com.hyland.openarch.springboot.rest.document.test:OSGI-INF/test-doc-constraints-contrib.xml")
@Deploy("com.hyland.openarch.springboot.rest.document.test:OSGI-INF/test-update-primary-type-contrib.xml")
@Import(DocumentAPITestConfiguration.class)
public abstract class AbstractDocumentAPITest extends AbstractAPITest {

    public static final String EXTERNAL_URL_UPLOAD_ID = "externalUrlUploadId";

    public static final String COMPLEX_UPLOAD_ID = "complexUploadId";

    public static final String MIMETYPE_TEXT_PLAIN = "text/plain";

    public static final String MIMETYPE_TEXT_URI_LIST = "text/uri-list";

    protected static final String ID_MARKER = "***ID_MARKER***";

    protected static final String NAME_MARKER = "***NAME_MARKER***";

    @Autowired
    UploadHandler handler;

    @Autowired
    UploadHandlerCleaner cleaner;

    @Autowired
    VocabularyService vocabularyService;

    @RuntimeInject
    protected CoreSession coreSession;

    @RuntimeInject
    DocumentModel doc;

    @RuntimeInject
    LoginService loginService;

    private String aclFolderSysId;

    private String aclFileSysId;

    protected abstract String getRootApiPath();

    protected abstract String getApiPath(String id, String path);

    protected String getApiPath(ResultActions result) throws Exception {
        return getApiPath(getJsonPath(result, SYS_ID), getJsonPath(result, SYS_PATH));
    }

    @Test
    public void testGet() throws Exception {
        checkResponse(NOT_FOUND, GET, REPO_ID, getApiPath("foo", "foo"));
        var result = checkResponse(OK, GET, REPO_ID, getRootApiPath());
        assertEquals(Versions.APP_HXCS_JSON_V1, result.andReturn().getResponse().getHeader(CONTENT_TYPE));
        ContentTestHelper.checkJson("rest/root.json", result);
    }

    @Test
    public void testGetV1() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.add(ACCEPT, Versions.APP_HXCS_JSON_V1);
        headers.add(HXCS_REPOSITORY, REPO_ID);
        var result = checkResponse(OK, GET, headers, getRootApiPath());
        assertEquals(Versions.APP_HXCS_JSON_V1, result.andReturn().getResponse().getHeader(CONTENT_TYPE));
        ContentTestHelper.checkJson("rest/root.json", result);
    }

    @Test
    public void testGetV0() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.add(ACCEPT, "application/vnd.hxcs.v0+json");
        headers.add(HXCS_REPOSITORY, REPO_ID);
        checkResponse(NOT_ACCEPTABLE, GET, headers, getRootApiPath());
    }

    @Test
    @WithUser(TEST_ANONYMOUS_USER)
    public void testGetUnauthorized() throws Exception {
        mvc.perform(get(getRootApiPath()).header(HXCS_REPOSITORY, REPO_ID))
                .andExpect(status().is(UNAUTHORIZED.getStatusCode()));
    }

    @Test
    @WithUser(ALT_USER)
    public void testGetForbidden() throws Exception {
        checkResponse(FORBIDDEN, GET, REPO_ID, getRootApiPath());
    }

    protected static String getCreationJson() {
        return """
                {
                  "sys_title": "My Folder",
                  "sys_description": "Test Folder for Document API",
                  "sys_primaryType": "SysFolder"
                }
                """;
    }

    protected String getCreationJsonWithACL() {
        return """
                {
                  "sys_title": "My Folder",
                  "sys_description": "Test Folder for Document API",
                  "sys_primaryType": "SysFolder",
                  "sys_acl": [
                    {
                        "user": {
                            "id": "myOtherUser",
                            "name": "myOtherUser"
                        },
                        "permission": "Everything",
                        "granted": true
                    },
                    {
                        "group": {
                            "id": "myGroup",
                            "name": "myGroup"
                        },
                        "permission": "ReadWrite",
                        "granted": true
                    }
                  ]
                }
                """;
    }

    protected String getReadAClJson(String userId){
        return """
                {
                    "sys_acl": [
                        {
                            "user": {
                            "id" : "%s"
                        },
                            "permission": "Read",
                                "granted": true
                        }
                    ]
                }
                """.formatted(userId);
    }

    protected String getInvalidCreationJson() {
        // missing type info
        return """
                {
                  "sys_title": "My Folder"
                }
                """;
    }

    protected String getInvalidCreationJson2() {
        // invalid type info
        return """
                {
                  "sys_title": "My Folder",
                  "sys_primaryType": "Unknown Type"
                }
                """;
    }

    protected String getInvalidCreationJson3() {
        // invalid property
        return """
                {
                  "dc_foo": "bar",
                  "sys_primaryType": "SysFolder"
                }
                """;
    }

    protected String getInvalidCreationJson4() {
        // invalid json format (missing last quote after '"SysFolder')
        return """
                {
                  "dc_foo": "bar",
                  "sys_primaryType": "SysFolder
                }
                """;
    }

    protected String getInvalidCreationJsonConstraints() {
        // misc constraints issues
        return """
                {
                  "cs_nature": "foo",
                  "cs_coverage": "bar",
                  "sys_primaryType": "DocWithConstraints"
                }
                """;
    }

    protected String getInvalidCreationJsonConversion() {
        // misc conversion issues
        return """
                {
                  "mydc_source": true,
                  "mydc_subjects": "foo",
                  "sys_primaryType": "SysFolder"
                }
                """;
    }

    protected String getUpdateJson() {
        return """
                {
                  "sys_title": "My Folder updated",
                  "mydc_source": "new source",
                  "mydc_issued": "2021-08-25T09:55:48.727Z",
                  "mydc_decimal": 1234567890.12345678901234567890
                }
                """;
    }

    protected String getInvalidUpdateJson() {
        return """
                {
                  "dc_foo": "bar"
                }
                """;
    }

    protected String getInvalidUpdateJsonConstraints() {
        // misc constraints issues
        return """
                {
                  "cs_nature": "foo",
                  "cs_coverage": "bar"
                }
                """;
    }

    protected String getInvalidUpdateJsonConversion() {
        // misc conversion issues
        return """
                {
                  "mydc_source": true,
                  "mydc_subjects": "foo"
                }
                """;
    }

    protected static void checkResponseBodyReplacing(String expectedFilePath, String id, String name,
            ResultActions resultActions) throws IOException, JSONException {
        String actual = resultActions.andReturn().getResponse().getContentAsString();
        String expected = ContentTestHelper.getReferenceContent(expectedFilePath);
        expected = expected.replace(ID_MARKER, id);
        expected = expected.replace(NAME_MARKER, name);
        JSONAssert.assertEquals(expected, actual, DOC_COMPARATOR);
    }

    protected void checkValidationResponse(HttpMethod method, String repoId, String jsonPayload, String path,
            String expectedFilePath) throws Exception {
        var response = mvc.perform(request(method, path).with(csrf())
                                                        .header(HXCS_REPOSITORY, repoId)
                                                        .contentType(APPLICATION_JSON)
                                                        .content(jsonPayload))
                          .andExpect(status().is(UNPROCESSABLE_ENTITY.getStatusCode()));
        ContentTestHelper.checkJson(expectedFilePath, response);
    }

    @Test
    public void testCreateUpdateAndDelete() throws Exception {
        String creationJson = getCreationJson();
        // invalid creation
        checkResponseWithPayload(NOT_FOUND, POST, REPO_ID, creationJson, getApiPath("foo", "foo"));
        // valid creation
        var result = checkResponseWithPayload(OK, POST, REPO_ID, creationJson, getRootApiPath());
        result.andExpectAll(jsonPath(SYS_ID).isNotEmpty(), jsonPath(SYS_PATH).isNotEmpty(),
                jsonPath(SYS_NAME).isNotEmpty(), jsonPath(SYS_IS_FOLDERISH).value(true));
        String id = getJsonPath(result, SYS_ID);
        String name = getJsonPath(result, SYS_NAME);
        String path = getJsonPath(result, SYS_PATH);
        checkResponseBodyReplacing("rest/folder.json", id, name, result);

        // check update
        String updateJson = getUpdateJson();
        // invalid update
        checkResponseWithPayload(NOT_FOUND, PUT, REPO_ID, updateJson, getApiPath("foo", "foo"));
        // valid update
        result = checkResponseWithPayload(OK, PUT, REPO_ID, updateJson, getApiPath(id, path));
        checkResponseBodyReplacing("rest/folder_updated.json", id, name, result);
        // check get again after update
        result = checkResponse(OK, GET, REPO_ID, getApiPath(id, path));
        checkResponseBodyReplacing("rest/folder_updated.json", id, name, result);

        // delete
        // invalid delete
        checkResponse(NOT_FOUND, DELETE, REPO_ID, getApiPath("foo", "foo"));
        // valid delete
        checkResponse(NO_CONTENT, DELETE, REPO_ID, getApiPath(id, path));
        // check get after delete
        checkResponse(NOT_FOUND, GET, REPO_ID, getApiPath(id, path));
    }

    @Test
    public void testCreateUpdateAndDeleteWithMultipartFormData() throws Exception {

        String creationJsonForMultipart = getBlobCreationJson("mainUploadId", "complexUploadId", "listUploadId");
        // invalid json
        checkResponseWithPayloadForMultipartFormData(BAD_REQUEST, POST, REPO_ID, "creationJsonForMultipart",
                getApiPath("foo", "foo"), new MockMultipartFile[] { getMainUploadIdPart() });
        // invalid creation
        checkResponseWithPayloadForMultipartFormData(NOT_FOUND, POST, REPO_ID, creationJsonForMultipart,
                getApiPath("foo", "foo"), new MockMultipartFile[] { getMainUploadIdPart() });
        // valid creation
        var result = checkResponseWithPayloadForMultipartFormData(OK, POST, REPO_ID, creationJsonForMultipart,
                getRootApiPath(),
                new MockMultipartFile[] { getMainUploadIdPart(), getComplexUploadIdPart(), getListUploadIdPart() });
        String id = getJsonPath(result, SYS_ID);
        String path = getJsonPath(result, SYS_PATH);
        String name = getJsonPath(result, SYS_NAME);

        // check update
        String updateJsonForMultipart = getBlobCreationJson("mainUploadId", "complexUploadId", "listUploadId");
        // invalid update
        checkResponseWithPayloadForMultipartFormData(NOT_FOUND, PUT, REPO_ID, updateJsonForMultipart,
                getApiPath("foo", "foo"),
                new MockMultipartFile[] { getMainUploadIdPart(), getComplexUploadIdPart(), getListUploadIdPart() });
        // valid update
        result = checkResponseWithPayloadForMultipartFormData(OK, PUT, REPO_ID, updateJsonForMultipart,
                getApiPath(id, path),
                new MockMultipartFile[] { getMainUploadIdPart(), getComplexUploadIdPart(), getListUploadIdPart() });
        checkResponseBodyReplacing("rest/complex_doc_with_blobs_for_multipart.json", id, name, result);
        // double-check that that blobs are indeed attached to document
        DocumentModel document = coreSession.getDocument(new IdRef(id));
        assertNotNull(document);
        // main
        Object mainBlob = document.getPropertyValue(SYSFILE_BLOB);
        assertInstanceOf(Blob.class, mainBlob);
        assertEquals("test content", ((Blob) mainBlob).getString());
        // complex
        Object complexBlob = document.getPropertyValue("tp_fileComplexList/0/blobItem");
        assertInstanceOf(Blob.class, complexBlob);
        assertEquals("test content 2", ((Blob) complexBlob).getString());
        // list
        Object listBlob = document.getPropertyValue("tp_fileList/0");
        assertInstanceOf(Blob.class, listBlob);
        assertEquals("test content 3", ((Blob) listBlob).getString());
        // check get again after update
        result = checkResponse(OK, GET, REPO_ID, getApiPath(id, path));
        checkResponseBodyReplacing("rest/complex_doc_with_blobs_for_multipart.json", id, name, result);
        // valid update
        result = checkResponseWithPayloadForMultipartFormData(OK, PUT, REPO_ID, updateJsonForMultipart,
                getApiPath(id, path),
                new MockMultipartFile[] { getMainUploadIdPart(), getComplexUploadIdPart(), getListUploadIdPart() });
        checkResponseBodyReplacing("rest/complex_doc_with_blobs_for_multipart.json", id, name, result);
        // check get again after update
        result = checkResponse(OK, GET, REPO_ID, getApiPath(id, path));
        checkResponseBodyReplacing("rest/complex_doc_with_blobs_for_multipart.json", id, name, result);

        // delete
        // valid delete
        checkResponse(NO_CONTENT, DELETE, REPO_ID, getApiPath(id, path));
        // check get after delete
        checkResponse(NOT_FOUND, GET, REPO_ID, getApiPath(id, path));
        // json + 1 binary part
        creationJsonForMultipart = getBlobCreationJsonWithOneBinary("mainUploadId");
        checkResponseWithPayloadForMultipartFormData(OK, POST, REPO_ID, creationJsonForMultipart, getRootApiPath(),
                new MockMultipartFile[] { getMainUploadIdPart() });

        // json + 2 binary part
        creationJsonForMultipart = getBlobCreationJsonWithTwoBinary("mainUploadId", "complexUploadId");
        checkResponseWithPayloadForMultipartFormData(OK, POST, REPO_ID, creationJsonForMultipart, getRootApiPath(),
                new MockMultipartFile[] { getMainUploadIdPart(), getComplexUploadIdPart() });

        // json + 3 binary part
        creationJsonForMultipart = getBlobCreationJsonWithThreeBinary("mainUploadId", "complexUploadId",
                "listUploadId");
        checkResponseWithPayloadForMultipartFormData(OK, POST, REPO_ID, creationJsonForMultipart, getRootApiPath(),
                new MockMultipartFile[] { getMainUploadIdPart(), getComplexUploadIdPart(), getListUploadIdPart() });

        // only json part
        checkResponseWithPayloadForMultipartFormData(OK, POST, REPO_ID, getCreationJson(), getRootApiPath(),
                new MockMultipartFile[] {});

    }

    @Test
    public void testMultipartDataWithOnlyInvalidPart_shouldFail() throws Exception {
        // no parts and binary
        checkResponseWithPayloadForMultipartFormData(BAD_REQUEST, POST, REPO_ID, null, getRootApiPath(),
                new MockMultipartFile[] {});
        // no parts and one binary
        checkResponseWithPayloadForMultipartFormData(BAD_REQUEST, POST, REPO_ID, null, getRootApiPath(),
                new MockMultipartFile[] { getMainUploadIdPart() });
        // binary part only
        checkResponseWithPayloadForMultipartFormData(BAD_REQUEST, POST, REPO_ID, null, getRootApiPath(),
                new MockMultipartFile[] { getMainUploadIdPart() });
        // invalid json
        checkResponseWithPayloadForMultipartFormData(BAD_REQUEST, POST, REPO_ID, getInvalidCreationJson(),
                getRootApiPath(), new MockMultipartFile[] {});
        // non-matching uploadIds
        String creationJsonForMultipart = getBlobCreationJsonWithThreeBinary("mainUploadId_nonMatching",
                "complexUploadId", "listUploadId");
        checkResponseWithPayloadForMultipartFormData(UNPROCESSABLE_ENTITY, POST, REPO_ID, creationJsonForMultipart,
                getRootApiPath(),
                new MockMultipartFile[] { getMainUploadIdPart(), getComplexUploadIdPart(), getListUploadIdPart() });
    }

    @Test
    public void testMultipartUpdateValidation() throws Exception {
        String creationJson = """
                {
                  "sys_title": "Test Document for Multipart Update Validation",
                  "sys_primaryType": "TestDocument"
                }
                """;
        var result = checkResponseWithPayload(OK, POST, REPO_ID, creationJson, getRootApiPath());

        String updateJson = """
                {
                  "mydc_subjects": "foo"
                }
                """;
        checkResponseWithPayloadForMultipartFormData(UNPROCESSABLE_ENTITY, PUT, REPO_ID, updateJson,
                getApiPath(result), new MockMultipartFile[] {}).andExpectAll(jsonPath("errors").isArray(),
                jsonPath("errors[0].path").value("mydc_subjects"));
    }

    @Test
    public void testInvalidCreate() throws Exception {
        // missing type
        String json = getInvalidCreationJson();
        var result = checkResponseWithPayload(BAD_REQUEST, POST, REPO_ID, json, getRootApiPath());
        checkErrorResponse(BAD_REQUEST, "Missing document type for document creation: primaryType", result);
        // invalid type
        json = getInvalidCreationJson2();
        result = checkResponseWithPayload(BAD_REQUEST, POST, REPO_ID, json, getRootApiPath());
        checkErrorResponse(BAD_REQUEST, "Unknown document type: Unknown Type", result);
        // invalid property
        json = getInvalidCreationJson3();
        result = checkResponseWithPayload(BAD_REQUEST, POST, REPO_ID, json, getRootApiPath());
        checkErrorResponse(BAD_REQUEST, "dc_foo", result);
        // invalid json
        json = getInvalidCreationJson4();
        result = checkResponseWithPayload(BAD_REQUEST, POST, REPO_ID, json, getRootApiPath());
        checkErrorResponse(BAD_REQUEST, "Invalid input", result);
    }

    @Test
    public void testCreateValidation() throws Exception {
        // Create the two vocabularies
        vocabularyService.createVocabulary(coreSession.getRepositoryName(), new CreateVocabulary("nature"));
        vocabularyService.createVocabulary(coreSession.getRepositoryName(), new CreateVocabulary("coverage"));

        // invalid constraint on vocabulary id
        String json = getInvalidCreationJsonConstraints();
        checkValidationResponse(POST, REPO_ID, json, getRootApiPath(), "rest/validation_constraints.json");
        json = getInvalidCreationJsonConversion();
        checkValidationResponse(POST, REPO_ID, json, getRootApiPath(), "rest/validation_conversion.json");

        // Remove the vocabularies
        vocabularyService.removeVocabulary(coreSession.getRepositoryName(), "nature");
        vocabularyService.removeVocabulary(coreSession.getRepositoryName(), "coverage");
    }

    @Test
    public void testInvalidUpdate() throws Exception {
        // invalid property
        String json = getInvalidUpdateJson();
        var result = checkResponseWithPayload(BAD_REQUEST, PUT, REPO_ID, json, getRootApiPath());
        checkErrorResponse(BAD_REQUEST, "dc_foo", result);
    }

    @Test
    public void testUpdateValidation() throws Exception {
        // Create the two vocabularies
        vocabularyService.createVocabulary(coreSession.getRepositoryName(), new CreateVocabulary("nature"));
        vocabularyService.createVocabulary(coreSession.getRepositoryName(), new CreateVocabulary("coverage"));

        DocumentModel docConstraints = coreSession.createDocumentModel("/", "docconstraints", "DocWithConstraints");
        docConstraints.create();
        coreSession.save();
        String path = getApiPath(docConstraints.getId(), docConstraints.getPathAsString());

        String json = getInvalidUpdateJsonConstraints();
        checkValidationResponse(PUT, REPO_ID, json, path, "rest/validation_constraints.json");
        json = getInvalidUpdateJsonConversion();
        checkValidationResponse(PUT, REPO_ID, json, path, "rest/validation_conversion.json");

        // Remove the vocabularies
        vocabularyService.removeVocabulary(coreSession.getRepositoryName(), "nature");
        vocabularyService.removeVocabulary(coreSession.getRepositoryName(), "coverage");
    }

    @Test
    public void testConditionalUpdate() throws Exception {
        String creationJson = getCreationJson();
        var result = checkResponseWithPayload(OK, POST, REPO_ID, creationJson, getRootApiPath());
        String id = getJsonPath(result, SYS_ID);
        String name = getJsonPath(result, SYS_NAME);
        String path = getJsonPath(result, SYS_PATH);

        // check update with condition that matches
        String updateJson = getUpdateJson();
        String condition = "sys_title = 'My Folder'";
        result = checkResponseWithPayload(OK, PUT, REPO_ID, updateJson,
                getApiPath(id, path) + "?" + CONDITION + "=" + condition);
        checkResponseBodyReplacing("rest/folder_updated.json", id, name, result);

        // check update with condition that does not match
        updateJson = """
                {
                  "sys_title": "My Folder updated again"
                }
                """;
        condition = "sys_title = 'no-such-title'";
        checkResponseWithPayload(PRECONDITION_FAILED, PUT, REPO_ID, updateJson,
                getApiPath(id, path) + "?" + CONDITION + "=" + condition);

        // check again after failed update, nothing changed
        result = checkResponse(OK, GET, REPO_ID, getApiPath(id, path));
        checkResponseBodyReplacing("rest/folder_updated.json", id, name, result);

        // cleanup
        checkResponseWithPayload(NO_CONTENT, DELETE, REPO_ID, "", getApiPath(id, path));
    }

    @Test
    public void testConditionalUpdateIf() throws Exception {
        String creationJson = getCreationJson();
        var result = checkResponseWithPayload(OK, POST, REPO_ID, creationJson, getRootApiPath());
        String id = getJsonPath(result, SYS_ID);
        String name = getJsonPath(result, SYS_NAME);
        String path = getJsonPath(result, SYS_PATH);

        // check update with expression condition that matches
        String updateJson = getUpdateJson();
        String expr = "content.sys_title == \"My Folder\"";
        result = checkResponseWithPayload(OK, PUT, REPO_ID, updateJson,
                getApiPath(id, path) + "?" + IF + "=" + expr);
        checkResponseBodyReplacing("rest/folder_updated.json", id, name, result);

        // check update with expression condition that does not match
        updateJson = """
                {
                  "sys_title": "My Folder updated again"
                }
                """;
        expr = "content.sys_title == \"no-such-title\"";
        checkResponseWithPayload(PRECONDITION_FAILED, PUT, REPO_ID, updateJson,
                getApiPath(id, path) + "?" + IF + "=" + expr);

        // check again after failed update, nothing changed
        result = checkResponse(OK, GET, REPO_ID, getApiPath(id, path));
        checkResponseBodyReplacing("rest/folder_updated.json", id, name, result);

        // an invalid expression is rejected
        expr = "this is not an expression";
        result = checkResponseWithPayload(BAD_REQUEST, PUT, REPO_ID, updateJson,
                getApiPath(id, path) + "?" + IF + "=" + expr);
        checkErrorResponse(BAD_REQUEST, Pattern.compile("Invalid expression: " + expr + ": .*"), result);

        // condition and if are mutually exclusive
        result = checkResponseWithPayload(BAD_REQUEST, PUT, REPO_ID, updateJson,
                getApiPath(id, path) + "?" + CONDITION + "=sys_title = 'My Folder'" + "&" + IF
                        + "=content.sys_title == \"My Folder\"");
        checkErrorResponse(BAD_REQUEST, "Cannot use both condition and if parameters", result);

        // cleanup
        checkResponseWithPayload(NO_CONTENT, DELETE, REPO_ID, "", getApiPath(id, path));
    }

    @Test
    public void testComplexDocument() throws Exception {
        TransactionHelper.startTransaction();
        doc.refresh();
        String docId = doc.getId();
        String path = doc.getPathAsString();
        String name = doc.getName();
        TransactionHelper.commitOrRollbackTransaction();
        assertNotNull(docId);
        var result = checkResponse(OK, GET, REPO_ID, getApiPath(docId, path));
        checkResponseBodyReplacing("rest/complex_doc.json", docId, name, result);
    }

    @Test
    public void testUploadBlobs() throws Exception {
        // create uploads to be referenced
        // main file
        Upload mainUpload = handler.newUpload();
        String mainUploadId = mainUpload.getId();
        String mainData = "test content";
        mainUpload.addBlob(Blobs.createBlob(mainData, TEXT_PLAIN, UTF_8, "test.txt"));
        // file for tp_fileComplexList
        Upload complexUpload = handler.newUpload();
        String complexUploadId = complexUpload.getId();
        String complexData = "test content 2";
        complexUpload.addBlob(Blobs.createBlob(complexData, TEXT_PLAIN, UTF_8, "test2.txt"));
        // file for tp_fileList
        Upload listUpload = handler.newUpload();
        String listUploadId = listUpload.getId();
        String listData = "test content 3";
        listUpload.addBlob(Blobs.createBlob(listData, TEXT_PLAIN, UTF_8, "test3.txt"));
        // additional file
        Upload additionalUpload = handler.newUpload();
        String additionalUploadId = additionalUpload.getId();
        additionalUpload.addBlob(Blobs.createBlob("foo", TEXT_PLAIN, UTF_8, "test.txt"));

        // check that uploads are there
        assertNotNull(handler.getUpload(mainUploadId));
        assertNotNull(handler.getUpload(complexUploadId));
        assertNotNull(handler.getUpload(listUploadId));
        assertNotNull(handler.getUpload(additionalUploadId));
        assertEquals(Set.of(), cleaner.get());

        String creationJson = getBlobCreationJson(mainUploadId, complexUploadId, listUploadId);
        var result = checkResponseWithPayload(OK, POST, REPO_ID, creationJson, getRootApiPath()).andExpectAll(
                jsonPath(SYS_ID).isNotEmpty(), jsonPath(SYS_NAME).isNotEmpty());

        String docId = getJsonPath(result, SYS_ID);
        String name = getJsonPath(result, SYS_NAME);
        checkResponseBodyReplacing("rest/complex_doc_with_blobs.json", docId, name, result);

        // double-check that that blobs are indeed attached to document
        DocumentModel document = coreSession.getDocument(new IdRef(docId));
        assertNotNull(document);
        // main
        Object mainBlob = document.getPropertyValue(SYSFILE_BLOB);
        assertInstanceOf(Blob.class, mainBlob);
        assertEquals(mainData, ((Blob) mainBlob).getString());
        // complex
        Object complexBlob = document.getPropertyValue("tp_fileComplexList/0/blobItem");
        assertInstanceOf(Blob.class, complexBlob);
        assertEquals(complexData, ((Blob) complexBlob).getString());
        // list
        Object listBlob = document.getPropertyValue("tp_fileList/0");
        assertInstanceOf(Blob.class, listBlob);
        assertEquals(listData, ((Blob) listBlob).getString());

        // check that uploads have had their blob storage released, but the entries are kept around so the attach
        // status (and attachDocId) can still be polled by the client (regression test for HXCS-8376)
        Upload mainUploadAfter = handler.getUpload(mainUploadId);
        assertNotNull(mainUploadAfter);
        assertTrue(mainUploadAfter.isReleased());
        assertEquals(UploadAttachStatus.READY, mainUploadAfter.getAttachStatus());
        assertEquals(docId, mainUploadAfter.getAttachDocId());
        Upload complexUploadAfter = handler.getUpload(complexUploadId);
        assertNotNull(complexUploadAfter);
        assertTrue(complexUploadAfter.isReleased());
        assertEquals(UploadAttachStatus.READY, complexUploadAfter.getAttachStatus());
        assertEquals(docId, complexUploadAfter.getAttachDocId());
        Upload listUploadAfter = handler.getUpload(listUploadId);
        assertNotNull(listUploadAfter);
        assertTrue(listUploadAfter.isReleased());
        assertEquals(UploadAttachStatus.READY, listUploadAfter.getAttachStatus());
        assertEquals(docId, listUploadAfter.getAttachDocId());
        // not used: still there, untouched
        Upload additionalUploadAfter = handler.getUpload(additionalUploadId);
        assertNotNull(additionalUploadAfter);
        assertFalse(additionalUploadAfter.isReleased());
        assertEquals(UploadAttachStatus.PENDING, additionalUploadAfter.getAttachStatus());
        assertEquals(Set.of(), cleaner.get());
    }

    @Test
    public void testPatch() throws Exception {
        String apiPath = String.format("%s/%s", PATH, ROOT_ID);
        String creationJson = """
                {
                  "sys_title": "My Document",
                  "sys_description": "Test Document for Document API",
                  "sys_primaryType": "TestDocument"
                }
                """;
        var result = checkResponseWithPayload(OK, POST, REPO_ID, creationJson, apiPath);
        apiPath = getApiPath(result);

        // 1. test that document type is TestDocument
        // 2. modify sys_title
        // 3. modify mydc_source
        // 4. modify tp_listOfLists by adding a new list (pizza) with a few items
        // 5. remove the 4th item (sushi) of the list
        // 6. add another list (dessert) to tp_listOfLists
        // 7. copy the 2nd item of the 2nd list to the end of the 1st list (flour copied from dessert to pizza)
        // 8. move the 2nd item of the 1st list to the end of the 2nd list (pineapple moved from pizza to dessert)
        String patchJson = """
                [
                  { "op": "test", "path": "/sys_primaryType", "value": "TestDocument" },
                  { "op": "replace", "path": "/sys_title", "value": "My Document updated" },
                  { "op": "add", "path": "/mydc_source", "value": "Source" },
                  { "op": "add", "path": "/tp_listOfLists", "value":
                    [
                      {
                        "stringItem": "belongs in pizza",
                        "stringListItem": [ "mozzarella", "pineapple", "pepperoni", "sushi" ]
                      }
                    ]
                  },
                  { "op": "remove", "path": "/tp_listOfLists/0/stringListItem/3" },
                  { "op": "add", "path": "/tp_listOfLists/-", "value":
                    {
                      "stringItem": "belongs in dessert",
                      "stringListItem": [ "apple", "flour", "chocolate" ]
                    }
                  },
                  { "op": "copy", "from": "/tp_listOfLists/1/stringListItem/1", "path": "/tp_listOfLists/0/stringListItem/-" },
                  { "op": "move", "from": "/tp_listOfLists/0/stringListItem/1", "path": "/tp_listOfLists/1/stringListItem/-" }
                ]
                """;
        String patchMediaType = MediaType.APPLICATION_JSON_PATCH_JSON;
        checkResponseWithPayload(OK, PATCH, patchMediaType, REPO_ID, patchJson, apiPath).andExpectAll(
                jsonPath(SYS_TITLE).value("My Document updated"), //
                jsonPath("mydc_source").value("Source"), //
                jsonPath("tp_listOfLists").isArray(), //
                jsonPath("tp_listOfLists[0].stringItem").value("belongs in pizza"), //
                jsonPath("tp_listOfLists[0].stringListItem").isArray(), //
                jsonPath("tp_listOfLists[0].stringListItem", contains("mozzarella", "pepperoni", "flour")), //
                jsonPath("tp_listOfLists[1].stringItem").value("belongs in dessert"), //
                jsonPath("tp_listOfLists[1].stringListItem").isArray(), //
                jsonPath("tp_listOfLists[1].stringListItem", contains("apple", "flour", "chocolate", "pineapple")));

        // Test a failing JSON Patch test operation:
        // 1. test that document type is SysFolder (will fail - document is actually TestDocument)
        // 2. modify sys_title (won't execute due to the failure of the previous test operation)
        patchJson = """
                [
                  { "op": "test", "path": "/sys_primaryType", "value": "SysFolder" },
                  { "op": "replace", "path": "sys_title", "value": "My Folder" }
                ]
                """;
        // Expects 400 Bad Request with proper JSON Patch error message
        checkResponseWithPayload(BAD_REQUEST, PATCH, patchMediaType, REPO_ID, patchJson, apiPath);
    }

    @Test
    public void testPatchValidation() throws Exception {
        String creationJson = """
                {
                  "sys_title": "Test Document for Patch Validation",
                  "sys_primaryType": "TestDocument"
                }
                """;
        var result = checkResponseWithPayload(OK, POST, REPO_ID, creationJson, getRootApiPath());
        String apiPath = getApiPath(result);

        String patchJson = """
                [
                  { "op": "add", "path": "/mydc_subjects", "value": "foo" }
                ]
                """;

        checkResponseWithPayload(UNPROCESSABLE_ENTITY, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, patchJson,
                apiPath).andExpectAll(jsonPath("errors").isArray(),
                jsonPath("errors[0].path").value("mydc_subjects"));
    }

    @Test
    public void testPatchAddToEmptyList() throws Exception {
        String apiPath = String.format("%s/%s", PATH, ROOT_ID);
        String creationJson = """
                {
                  "sys_title": "Test Document for Empty List",
                  "sys_primaryType": "TestDocument"
                }
                """;
        var result = checkResponseWithPayload(OK, POST, REPO_ID, creationJson, apiPath);
        apiPath = getApiPath(result);

        // Try to add to an empty list (usually not present at all but here it's an actual empty list)
        String patchJson = """
                [
                  { "op": "add", "path": "/mydc_subjects/-", "value": "foo" }
                ]
                """;

        // Should return 200 with proper udpated value
        checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, patchJson, apiPath).andExpectAll(
                jsonPath("mydc_subjects").isArray(), //
                jsonPath("mydc_subjects[0]").value("foo"));
    }

    @Test
    public void testPatchNonExistentProperty() throws Exception {
        // Test PATCH with missing property returns 400 Bad Request with proper error message
        String apiPath = String.format("%s/%s", PATH, ROOT_ID);
        String creationJson = """
                {
                  "sys_title": "Test Document for Missing Property",
                  "sys_primaryType": "TestDocument"
                }
                """;
        var result = checkResponseWithPayload(OK, POST, REPO_ID, creationJson, apiPath);
        apiPath = getApiPath(result);

        // Try to add to a non-existent property
        String patchJson = """
                [
                  { "op": "add", "path": "/nonexistent_property/-", "value": "some value" }
                ]
                """;

        // Should return 400 Bad Request with JsonPatchApplicationException message
        checkResponseWithPayload(BAD_REQUEST, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, patchJson, apiPath);
    }

    @Test
    public void testPatchTestOperationOnNonExistentProperty() throws Exception {
        // Test that test operations on missing properties return proper JSON Patch errors
        String apiPath = String.format("%s/%s", PATH, ROOT_ID);
        String creationJson = """
                {
                  "sys_title": "Test Document for Test Operation",
                  "sys_primaryType": "TestDocument"
                }
                """;
        var result = checkResponseWithPayload(OK, POST, REPO_ID, creationJson, apiPath);
        apiPath = getApiPath(result);

        // Try to test a non-existent property
        String patchJson = """
                [
                  { "op": "test", "path": "/nonexistent_property", "value": "some value" }
                ]
                """;

        // Should return 400 Bad Request with proper JSON Patch error message
        checkResponseWithPayload(BAD_REQUEST, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, patchJson, apiPath);
    }

    @Test
    public void testPatchSystemProperties() throws Exception {
        String apiPath = String.format("%s/%s", PATH, ROOT_ID);
        String creationJson = """
                {
                  "sys_title": "My Document",
                  "sys_description": "My Test Document",
                  "sys_primaryType": "TestDocument"
                }
                """;
        var result = checkResponseWithPayload(OK, POST, REPO_ID, creationJson, apiPath);
        apiPath = getApiPath(result);

        String patchJson = """
                [
                  { "op": "add", "path": "/sys_lifecycleState", "value": "draft" }
                ]
                """;
        String patchMediaType = MediaType.APPLICATION_JSON_PATCH_JSON;
        checkResponseWithPayload(OK, PATCH, patchMediaType, REPO_ID, patchJson, apiPath).andExpect(
                jsonPath("sys_lifecycleState").value("draft"));
    }

    @Test
    public void testConditionalPatch() throws Exception {
        // create document
        String creationJson = """
                {
                  "sys_primaryType": "SysFile",
                  "sys_title": "My Doc"
                }
                """;
        var result = checkResponseWithPayload(OK, POST, REPO_ID, creationJson, getRootApiPath());
        String apiPath = getApiPath(result);

        // patch with condition that does not match
        String patchJson = """
                [
                  { "op": "add", "path": "/sys_title", "value": "My Doc patched" }
                ]
                """;
        String condition = "sys_title = 'no-such-title'";
        checkResponseWithPayload(PRECONDITION_FAILED, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, patchJson,
                apiPath + "?" + CONDITION + "=" + condition);
        // after failed patch nothing changed
        checkResponse(OK, GET, REPO_ID, apiPath).andExpect(jsonPath("sys_title").value("My Doc"));

        // patch with condition that matches
        condition = "sys_title = 'My Doc'";
        result = checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON, REPO_ID, patchJson,
                apiPath + "?" + CONDITION + "=" + condition);
        // patch was applied
        result.andExpect(jsonPath("sys_title").value("My Doc patched"));
    }

    @Test
    public void testUploadBlobsSecurityFailure() throws Exception {
        // create upload to be referenced with another user
        String mainUploadId;
        String complexUploadId;
        String listUploadId;
        String additionalUploadId;
        try (var __ = loginService.loginPrincipal(ALT_PRINCIPAL)) {
            // main file
            Upload mainUpload = handler.newUpload();
            mainUploadId = mainUpload.getId();
            String mainData = "test content";
            mainUpload.addBlob(Blobs.createBlob(mainData, TEXT_PLAIN, UTF_8, "test.txt"));
            // file for tp_fileComplexList
            Upload complexUpload = handler.newUpload();
            complexUploadId = complexUpload.getId();
            String complexData = "test content 2";
            complexUpload.addBlob(Blobs.createBlob(complexData, TEXT_PLAIN, UTF_8, "test2.txt"));
            // file for tp_fileList
            Upload listUpload = handler.newUpload();
            listUploadId = listUpload.getId();
            String listData = "test content 3";
            listUpload.addBlob(Blobs.createBlob(listData, TEXT_PLAIN, UTF_8, "test3.txt"));
            // additional file
            Upload additionalUpload = handler.newUpload();
            additionalUploadId = additionalUpload.getId();
            additionalUpload.addBlob(Blobs.createBlob("foo", TEXT_PLAIN, UTF_8, "test.txt"));

            // check that uploads are there
            assertNotNull(handler.getUpload(mainUploadId));
            assertNotNull(handler.getUpload(complexUploadId));
            assertNotNull(handler.getUpload(listUploadId));
            assertNotNull(handler.getUpload(additionalUploadId));
            assertEquals(Set.of(), cleaner.get());
        }
        // check that main upload cannot be accessed by user
        try {
            handler.getUpload(mainUploadId);
            fail("Should have thrown DocumentSecurityException");
        } catch (DocumentSecurityException e) {
            // ok
        }

        // check creation fails
        String creationJson = getBlobCreationJson(mainUploadId, complexUploadId, listUploadId);
        checkResponseWithPayload(FORBIDDEN, POST, REPO_ID, creationJson, getRootApiPath());

        // check that uploads have not been cleaned
        try (var __ = loginService.loginPrincipal(ALT_PRINCIPAL)) {
            assertNotNull(handler.getUpload(mainUploadId));
            assertNotNull(handler.getUpload(complexUploadId));
            assertNotNull(handler.getUpload(listUploadId));
            assertNotNull(handler.getUpload(additionalUploadId));
            assertEquals(Set.of(), cleaner.get());
        }
    }

    @Test
    @WithUser(ALT_USER)
    public void testCreateDocumentAsNonAdminUserUnderAdminOwnedFolder() throws Exception {
        // non-admin user cannot access root document
        var getRootResult = checkResponseWithPayload(FORBIDDEN, GET, REPO_ID, null, getRootApiPath());
        checkErrorResponse(FORBIDDEN, "Privilege 'Read' is not granted to '%s' on document '%s'".formatted(ALT_USER_ID, ROOT_ID), getRootResult);

        // create an upload as a non-admin user
        Upload upload;
        try (var __ = loginService.loginPrincipal(ALT_PRINCIPAL)) {
            upload = handler.newUpload();
        }
        String uploadId = upload.getId();
        String uploadData = "test content";
        upload.addBlob(Blobs.createBlob(uploadData, TEXT_PLAIN, UTF_8, "test.txt"));
        assertNotNull(upload.getId());
        assertEquals(ALT_USER_ID, upload.getUser());
        assertEquals("test.txt", upload.getFileName());
        assertEquals(uploadData, upload.getBlob().getString());

        // create a folder under root as an admin user
        doc = coreSession.createDocumentModel("/", "My Folder", "SysFolder");
        String folderId = doc.getId();
        doc.create();
        coreSession.save();
        assertEquals(TEST_ADMIN_USER, doc.getPrincipal().getId());

        // create a document with sysfile_blob as non-admin user under folder owned by admin
        String createDocumentPayload = """
                {
                     "sys_name":"test.txt",
                     "sys_primaryType":"SysFile",
                     "sys_parentId":"%s",
                     "sys_title":"test.txt",
                     "sysfile_blob":{"uploadId":"%s"}
                 }
                """.formatted(folderId, uploadId);

        // as admin granted no permissions on the folder to non-admin user it should be forbidden
        checkResponseWithPayload(FORBIDDEN, POST, REPO_ID, createDocumentPayload, PATH + "/" + doc.getId());

        // Now set EVERYTHING ACL for non-admin user on the folder (owned by admin) by admin user
        mvc.perform(put(PATH + "/" + doc.getId()).with(csrf())
                                                 .with(user(TEST_ADMIN_USER))
                                                 .header(HXCS_REPOSITORY, REPO_ID)
                                                 .content("""
                                                         {
                                                             "sys_acl": [
                                                                 {
                                                                       "permission": "%s",
                                                                       "granted": true,
                                                                       "user": {
                                                                             "id": "%s"
                                                                       }
                                                                 }
                                                             ]
                                                         }
                                                         """.formatted(EVERYTHING, ALT_USER_ID))
                                                 .contentType(APPLICATION_JSON)
                                                 .accept(Versions.APP_HXCS_JSON_V1)).andExpect(status().isOk());

        // retry document creation
        checkResponseWithPayload(OK, POST, REPO_ID, createDocumentPayload, PATH + "/" + doc.getId());
    }

    @Test
    public void testUploadBlobsFailure() throws Exception {
        // create upload to be referenced
        // main file
        Upload upload = handler.newUpload();
        String uploadId = upload.getId();
        upload.addBlob(Blobs.createBlob("test content", TEXT_PLAIN, UTF_8, "test.txt"));

        // check that upload is there
        assertNotNull(handler.getUpload(uploadId));
        assertEquals(Set.of(), cleaner.get());

        String creationJson = getInvalidBlobCreationJson(uploadId);
        var result = checkResponseWithPayload(BAD_REQUEST, POST, REPO_ID, creationJson, getRootApiPath());
        checkErrorResponse(BAD_REQUEST, "sysfile_blob", result);

        // check that upload handler did not clean up the upload after unsuccessful creation
        assertNotNull(handler.getUpload(uploadId));
        assertEquals(Set.of(), cleaner.get());
    }

    @Test
    public void testTypeAndMixinsUpdate() throws Exception {
        // create a standard file document with a mixin
        String creationJson = """
                {
                  "sys_primaryType": "SysFile",
                  "sys_mixinTypes": [ "CustomFacet" ],
                  "customschema_string": "test",
                  "sys_title": "My Changing File"
                }
                """;
        var result = checkResponseWithPayload(OK, POST, REPO_ID, creationJson, getRootApiPath())
                // validate existing mixins
                .andExpectAll(jsonPath(SYS_MIXIN_TYPES + ".length()", is(2)),
                        jsonPath(SYS_MIXIN_TYPES, hasItems("CustomFacet")));

        String apiPath = getApiPath(result);
        // change type to a SysFolder and add SysOrderable mixin
        String payload = """
                {
                  "sys_primaryType": "SysFolder",
                  "sys_mixinTypes": [ "SysOrderable", "CustomFacet" ]
                }
                """;
        checkResponseWithPayload(OK, PUT, REPO_ID, payload, apiPath)
                // validate the updated mixins (the custom field value should be unchanged)
                .andExpect(jsonPath(SYS_MIXIN_TYPES + ".length()", is(3)))
                .andExpect(jsonPath(SYS_MIXIN_TYPES, contains("CustomFacet", SYS_FOLDERISH, SYS_ORDERABLE)))
                .andExpect(jsonPath("customschema_string").value("test"));

        // remove the SysOrderable and SysFolderish mixins (the latter will reappear due to primary type)
        payload = """
                {
                  "sys_mixinTypes": [ "CustomFacet" ]
                }
                """;
        checkResponseWithPayload(OK, PUT, REPO_ID, payload, apiPath)
                // validate the updated mixins (the field value should remain the same)
                .andExpect(jsonPath(SYS_MIXIN_TYPES + ".length()", is(2)))
                .andExpect(jsonPath(SYS_MIXIN_TYPES, contains("CustomFacet", SYS_FOLDERISH)))
                .andExpect(jsonPath("customschema_string").value("test"));

        // remove the custom mixin
        payload = """
                {
                  "sys_mixinTypes": [ ]
                }
                """;
        checkResponseWithPayload(OK, PUT, REPO_ID, payload, apiPath)
                // validate that the mixins are back to their initial state
                .andExpect(jsonPath(SYS_MIXIN_TYPES + ".length()", is(1)))
                .andExpect(jsonPath(SYS_MIXIN_TYPES, contains(SYS_FOLDERISH)));

        // re-add the custom mixin including some new data
        payload = """
                {
                  "sys_mixinTypes": [ "SysOrderable", "CustomFacet" ],
                  "customschema_string": "test2"
                }
                """;
        checkResponseWithPayload(OK, PUT, REPO_ID, payload, apiPath)
                // validate the updated mixins and the new data
                .andExpect(jsonPath(SYS_MIXIN_TYPES + ".length()", is(3)))
                .andExpect(jsonPath(SYS_MIXIN_TYPES, contains("CustomFacet", SYS_FOLDERISH, SYS_ORDERABLE)))
                .andExpect(jsonPath("customschema_string").value("test2"));

    }

    @Test
    public void testTypeAndPropertyUpdate() throws Exception {
        // create a standard file document
        String creationJson = """
                {
                  "sys_primaryType": "SysFile",
                  "sys_title": "My Changing File"
                }
                """;
        var result = checkResponseWithPayload(OK, POST, REPO_ID, creationJson, getRootApiPath())
                // validate the creation of the document
                .andExpect(jsonPath(SYS_MIXIN_TYPES + ".length()", is(1)))
                .andExpect(jsonPath(SYS_MIXIN_TYPES, containsInAnyOrder(SYS_FILISH)));

        String apiPath = getApiPath(result);
        // update the document type
        String payload = """
                {
                  "sys_primaryType": "File2"
                }
                """;
        checkResponseWithPayload(OK, PUT, REPO_ID, payload, apiPath)
                // validate the updated mixins and the new type
                .andExpect(jsonPath(SYS_MIXIN_TYPES + ".length()", is(1)))
                .andExpect(jsonPath(SYS_MIXIN_TYPES, containsInAnyOrder("File2Facet")))
                .andExpect(jsonPath(SYS_PRIMARY_TYPE).value("File2"));

        // update the value of a property coming from the new document type's mixin
        payload = """
                {
                  "mcs2_prop": "Test value"
                }
                """;
        checkResponseWithPayload(OK, PUT, REPO_ID, payload, apiPath)
                // validate the updated properties
                .andExpect(jsonPath("mcs2_prop").value("Test value"));
    }

    @Test
    public void testMixinsUpdate() throws Exception {
        // create a standard folder document
        String creationJson = getCreationJson();
        var result = checkResponseWithPayload(OK, POST, REPO_ID, creationJson, getRootApiPath())
                // validate existing mixins
                .andExpect(jsonPath(SYS_MIXIN_TYPES + ".length()", is(1)))
                .andExpect(jsonPath(SYS_MIXIN_TYPES, contains(SYS_FOLDERISH)));

        String apiPath = getApiPath(result);

        // add the SysOrderable mixin and a custom mixin, with a custom field and value
        String payload = """
                {
                  "sys_mixinTypes": [ "SysOrderable", "CustomFacet" ],
                  "customschema_string": "test"
                }
                """;
        checkResponseWithPayload(OK, PUT, REPO_ID, payload, apiPath)
                // validate the updated mixins (the field value should also be updated)
                .andExpect(jsonPath(SYS_MIXIN_TYPES + ".length()", is(3)))
                .andExpect(jsonPath(SYS_MIXIN_TYPES, containsInAnyOrder(SYS_FOLDERISH, SYS_ORDERABLE, "CustomFacet")))
                .andExpect(jsonPath("customschema_string").value("test"));

        // remove the SysOrderable mixin and SysFolderish mixin (which will reappear due to primary type)
        payload = """
                {
                  "sys_mixinTypes": [ "CustomFacet" ]
                }
                """;
        checkResponseWithPayload(OK, PUT, REPO_ID, payload, apiPath)
                // validate the updated mixins (the field value should remain the same)
                .andExpect(jsonPath(SYS_MIXIN_TYPES + ".length()", is(2)))
                .andExpect(jsonPath(SYS_MIXIN_TYPES, containsInAnyOrder(SYS_FOLDERISH, "CustomFacet")))
                .andExpect(jsonPath("customschema_string").value("test"));

        // remove the custom mixin
        payload = """
                {
                  "sys_mixinTypes": [ ]
                }
                """;
        checkResponseWithPayload(OK, PUT, REPO_ID, payload, apiPath)
                // validate that the mixins are back to their initial state
                .andExpect(jsonPath(SYS_MIXIN_TYPES + ".length()", is(1)))
                .andExpect(jsonPath(SYS_MIXIN_TYPES, containsInAnyOrder(SYS_FOLDERISH)));

        // re-add the custom mixin
        payload = """
                {
                  "sys_mixinTypes": [ "SysOrderable", "CustomFacet" ]
                }
                """;
        checkResponseWithPayload(OK, PUT, REPO_ID, payload, apiPath)
                // validate the updated mixins (the field should be empty)
                .andExpect(jsonPath(SYS_MIXIN_TYPES + ".length()", is(3)))
                .andExpect(jsonPath(SYS_MIXIN_TYPES, hasItems(SYS_FOLDERISH, "CustomFacet")))
                .andExpect(jsonPath("customschema_string").doesNotExist());
    }

    protected String getCreateFileJsonForDocumentACLUpdate() {
        return """
                {
                    "sys_primaryType": "SysFile",
                    "sys_title": "Acl_file"
                }
                """;
    }

    @Test
    public void shouldReturn400WhenTwoPrincipalsInACE() throws Exception {
        // create a standard folder document
        String creationJson = getCreateFileJsonForDocumentACLUpdate();
        var result = checkResponseWithPayload(OK, POST, REPO_ID, creationJson, getRootApiPath());
        String aclSysId = getJsonPath(result, SYS_ID);

        // add new sys_acl values
        String userAndGroupPrincipalPayload = """
                    {
                        "sys_acl": [
                            {
                                "user": {
                                    "id" : "Miguel"
                                },
                                "group": {
                                    "id" : "id",
                                    "name": "groupName"
                                },
                                "permission": "Read",
                                "granted": true
                            }
                        ]
                    }
                """;
        String aclFilePath = String.format("%s/%s", PATH, aclSysId);
        checkResponseWithPayload(BAD_REQUEST, PUT, REPO_ID, userAndGroupPrincipalPayload, aclFilePath);
    }

    @Test
    public void shouldReturn400WhenNoPrincipalInACE() throws Exception {
        // create a standard folder document
        String creationJson = getCreateFileJsonForDocumentACLUpdate();
        var result = checkResponseWithPayload(OK, POST, REPO_ID, creationJson, getRootApiPath());
        String aclSysId = getJsonPath(result, SYS_ID);

        // add new sys_acl values
        String userAndGroupPrincipalPayload = """
                    {
                        "sys_acl": [
                            {
                                "permission": "Read",
                                "granted": true
                            }
                        ]
                    }
                """;
        String aclFilePath = String.format("%s/%s", PATH, aclSysId);
        checkResponseWithPayload(BAD_REQUEST, PUT, REPO_ID, userAndGroupPrincipalPayload, aclFilePath);
    }

    @Test
    public void testACLUpdatePutOnlyAclUserDTO() throws Exception {
        // create a standard folder document
        String creationJson = getCreateFileJsonForDocumentACLUpdate();
        var result = checkResponseWithPayload(OK, POST, REPO_ID, creationJson, getRootApiPath());
        String aclSysId = getJsonPath(result, SYS_ID);

        // add new sys_acl values
        String payload = """
                    {
                        "sys_acl": [
                            {
                                "user": {
                                    "id" : "Miguel"
                                },
                                "permission": "Read",
                                "granted": true
                            }
                        ]
                    }
                """;
        String aclFilePath = String.format("%s/%s", PATH, aclSysId);
        checkResponseWithPayload(OK, PUT, REPO_ID, payload, aclFilePath).andExpectAll(
                jsonPath(SYS_ACL + "[0].granted").value(true), jsonPath(SYS_ACL + "[0].permission").value("Read"),
                jsonPath(SYS_ACL + "[0].user.id").value("Miguel"));
    }

    @Test
    public void testACLNoUpdate() throws Exception {
        String creationJson = getCreateFileJsonForDocumentACLUpdate();
        var result = checkResponseWithPayload(OK, POST, REPO_ID, creationJson, getRootApiPath());
        String id = getJsonPath(result, SYS_ID);
        String path = String.format("%s/%s", PATH, id);

        String acl = """
                [
                    {
                        "granted": true,
                        "permission": "Read",
                        "user": {
                            "id": "Miguel"
                        }
                    }
                ]
                """;

        // add sys_acl
        String updateAclJson = """
                    {
                        "sys_acl": %s
                    }
                """.formatted(acl);
        checkResponseWithPayload(OK, PUT, REPO_ID, updateAclJson, path).andExpectAll(
                jsonPath(SYS_ACL + "[0].granted").value(true), jsonPath(SYS_ACL + "[0].permission").value("Read"),
                jsonPath(SYS_ACL + "[0].user.id").value("Miguel"));

        // update sys_title with no sys_acl change
        String updateTitleJson = """
                {
                    "sys_title": "My file updated"
                }
                """;
        checkResponseWithPayload(OK, PUT, REPO_ID, updateTitleJson, path).andExpectAll(
                // sys_acl is still present and unchanged
                jsonPath(SYS_ACL + "[0].granted").value(true), jsonPath(SYS_ACL + "[0].permission").value("Read"),
                jsonPath(SYS_ACL + "[0].user.id").value("Miguel"));
    }

    @Test
    public void testACLUpdatePutOnlyAclGroupDTO() throws Exception {
        // create a standard folder document
        String creationJson = getCreateFileJsonForDocumentACLUpdate();
        var result = checkResponseWithPayload(OK, POST, REPO_ID, creationJson, getRootApiPath());
        String aclSysId = getJsonPath(result, SYS_ID);
        // when add new sys_acl values
        String payload = """
                    {
                        "sys_acl": [
                            {
                                "group": {
                                    "id" : "id",
                                    "name": "groupName"
                                },
                                "permission": "Read",
                                "granted": true
                            }
                        ]
                    }
                """;
        String aclFilePath = String.format("%s/%s", PATH, aclSysId);
        checkResponseWithPayload(OK, PUT, REPO_ID, payload, aclFilePath).andExpectAll(
                // then the acl should be present
                jsonPath(SYS_ACL + "[0].granted").value(true), jsonPath(SYS_ACL + "[0].permission").value("Read"),
                jsonPath(SYS_ACL + "[0].group.id").value("id"));

        // when putting the empty array to sys_acl
        String removePayload = """
                    {
                        "sys_acl": [
                        ]
                    }
                """;
        checkResponseWithPayload(OK, PUT, REPO_ID, removePayload, aclFilePath)
                // then whole sys_acl node is removed
                .andExpect(jsonPath(SYS_ACL).doesNotExist());
    }

    @Test
    public void testACLUpdatePatch() throws Exception {
        // create a standard folder document
        String creationJson = getCreateFileJsonForDocumentACLUpdate();
        var result = checkResponseWithPayload(OK, POST, REPO_ID, creationJson, getRootApiPath());
        String aclSysId = getJsonPath(result, SYS_ID);

        // add new sys_acl values
        String payload = """
                [
                {   "op": "add", "path": "/sys_acl", "value": [{
                        "group": {
                            "id" : "id",
                            "name": "groupName"
                        },
                        "permission": "Read",
                        "granted": true
                    }]
                }
                ]
                """;
        String aclFilePath = String.format("%s/%s", PATH, aclSysId);
        checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, payload,
                aclFilePath).andExpectAll(jsonPath(SYS_ACL + "[0].granted").value(true),
                jsonPath(SYS_ACL + "[0].permission").value("Read"),
                jsonPath(SYS_ACL + "[0].group.id").value("id"));

        // add new sys_acl values
        String newGroupACLPayload = """
                [
                {   "op": "add", "path": "/sys_acl/-", "value": {
                        "group": {
                            "id" : "id2",
                            "name": "newGroup"
                        },
                        "permission": "Read",
                        "granted": true
                    }
                }
                ]
                """;
        checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, newGroupACLPayload,
                aclFilePath).andExpectAll(jsonPath(SYS_ACL + "[1].granted").value(true),
                jsonPath(SYS_ACL + "[1].permission").value("Read"),
                jsonPath(SYS_ACL + "[1].group.id").value("id2"));

        ///////////////////////////////////////////////////////
        // modify the groupId with replace method
        String replacePayload = """
                [
                {   "op": "replace", "path": "/sys_acl/0/group/id", "value" : "id3"}
                ]
                """;
        checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, replacePayload,
                aclFilePath);

        // test the new groupId value with Patch test method
        String testPayload = """
                [
                {   "op": "test", "path": "/sys_acl/0/group/id", "value": "id3"}
                ]
                """;
        checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, testPayload, aclFilePath)
                // check if the new id is present also in the response
                .andExpect(jsonPath(SYS_ACL + "[0].group.id").value("id3"));

        // now check the removal of newId sys_acl
        String removePayload = """
                [
                {   "op": "remove", "path": "/sys_acl/0"}
                ]
                """;
        checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, removePayload,
                aclFilePath).andExpectAll(jsonPath(SYS_ACL + "[0].granted").value(true),
                jsonPath(SYS_ACL + "[0].permission").value("Read"),
                jsonPath(SYS_ACL + "[0].group.id").value("id2"));

        // now check the removal of the second sys_acl
        checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, removePayload,
                aclFilePath).andExpect(jsonPath(SYS_ACL).doesNotExist());
    }

    private String getCreateGovernableFolderJson() {
        return """
                {
                    "sys_primaryType": "SysFolder",
                    "sys_title": "My Governable Folder",
                    "sys_name": "My Governable Folder Name"
                }
                """;
    }

    private String getCreateFolderJsonForDocumentACLUpdate() {
        return """
                {
                    "sys_primaryType": "SysFolder",
                    "sys_title": "My ACL Folder",
                    "sys_name": "My ACL Folder Name"
                }
                """;
    }

    private String getCreateFileJsonForAclBlockInheritance() {
        return """
                {
                    "sys_primaryType": "SysFile",
                    "sys_title": "My File",
                    "sys_name": "My File Name"
                }
                """;
    }

    private String getCreateNonGovernableFileJson() {
        return """
                {
                    "sys_primaryType": "SysFile",
                    "sys_title": "My File",
                    "sys_name": "My File Name"
                }
                """;
    }

    private String getCreateGovernableFileWithRetentionJson(String retainUntil) {
        return """
                {
                    "sysgov_isRecord": true,
                    "sysgov_retainUntil": "%s"
                }
                """.formatted(retainUntil);
    }

    private String getCreateGovernableFileAsRecordJson() {
        return getCreateGovernableFileAsRecordJson("true"); // JSON boolean
    }

    private String getCreateGovernableFileAsRecordJson(String isRecord) {
        return """
                {
                    "sys_primaryType": "GovernableFile",
                    "sys_title": "My File",
                    "sys_name": "My File Name",
                    "sysgov_isRecord": %s
                }
                """.formatted(isRecord);
    }

    private String getUpdateIndeterminateRetention(String retainUntil) {
        return """
                {
                  "sysgov_retainUntil": "%s",
                  "sys_description": "Updated description"
                }
                """.formatted(retainUntil);
    }

    private String getCreateGovernableFileAsNonRecordJson() {
        return """
                {
                    "sys_primaryType": "GovernableFile",
                    "sys_title": "My File",
                    "sys_name": "My File Name"
                }
                """;
    }

    private String setLegalHoldWithOtherPropertiesJson(boolean hasLegalHold) {
        return """
                {
                  "sysgov_hasLegalHold": %s,
                  "sys_title": "My File",
                  "sys_name": "My File Name"
                }
                """.formatted(hasLegalHold);
    }

    private String setLegalHoldJson(boolean hasLegalHold) {
        return """
                {
                  "sysgov_hasLegalHold": %s
                }
                """.formatted(hasLegalHold);
    }

    private String setRetainUntilJson(String retainUntil) {
        return """
                {
                  "sysgov_retainUntil": "%s"
                }
                """.formatted(retainUntil);
    }

    private void createRetentionFolderAndFile(String payload) throws Exception {
        var folderResult = checkResponseWithPayload(OK, POST, REPO_ID, getCreateGovernableFolderJson(),
                getRootApiPath());
        aclFolderSysId = getJsonPath(folderResult, SYS_ID);
        String folderPath = String.format("%s/%s", PATH, aclFolderSysId);
        var responseSetAclFile = checkResponseWithPayload(OK, POST, REPO_ID, payload,
                folderPath);
        aclFileSysId = getJsonPath(responseSetAclFile, SYS_ID);
    }

    private void createACLFolderAndFile() throws Exception {
        var folderResult = checkResponseWithPayload(OK, POST, REPO_ID, getCreateFolderJsonForDocumentACLUpdate(),
                getRootApiPath());
        aclFolderSysId = getJsonPath(folderResult, SYS_ID);
        String folderPath = String.format("%s/%s", PATH, aclFolderSysId);
        var responseSetAclFile = checkResponseWithPayload(OK, POST, REPO_ID, getCreateFileJsonForAclBlockInheritance(),
                folderPath);
        aclFileSysId = getJsonPath(responseSetAclFile, SYS_ID);
    }

    private void cleanUpAfterACLTest() throws Exception {
        checkResponseWithPayload(NO_CONTENT, DELETE, REPO_ID, "", String.format("%s/%s", PATH, aclFileSysId));
        checkResponseWithPayload(NO_CONTENT, DELETE, REPO_ID, "", String.format("%s/%s", PATH, aclFolderSysId));
    }

    private void tryCleanupRecordsAfterRetentionTest() throws Exception {
        checkResponseWithPayload(FORBIDDEN, DELETE, REPO_ID, "", String.format("%s/%s", PATH, aclFileSysId));
        checkResponseWithPayload(FORBIDDEN, DELETE, REPO_ID, "", String.format("%s/%s", PATH, aclFolderSysId));
    }

    private void tryCleanupNonRecordsAfterRetentionTest() throws Exception {
        checkResponseWithPayload(NO_CONTENT, DELETE, REPO_ID, "", String.format("%s/%s", PATH, aclFileSysId));
        checkResponseWithPayload(NO_CONTENT, DELETE, REPO_ID, "", String.format("%s/%s", PATH, aclFolderSysId));
    }

    @Test
    public void testACLDefinedAtCreation() throws Exception {
        var folderResponse = checkResponseWithPayload(OK, POST, REPO_ID, getCreationJsonWithACL(),
                getRootApiPath());
        String id = getJsonPath(folderResponse, SYS_ID);
        ContentTestHelper.checkJson("rest/creation_folder_with_acl.json", folderResponse,
                DOC_COMPARATOR, id);
    }

    @Test
    public void testACLWithBlockInheritancePUT() throws Exception {
        createACLFolderAndFile();
        String payloadFolder = """
                {
                    "sys_acl": [
                        {
                            "user": {
                                "id" : "%s"
                            },
                            "permission": "Read",
                            "granted": true,
                            "creator": "%s"
                        }
                    ]
                }
                """.formatted(TEST_ADMIN_USER, TEST_ADMIN_USER);
        String folderPath = String.format("%s/%s", PATH, aclFolderSysId);
        var resultSetAclFolder = checkResponseWithPayload(OK, PUT, REPO_ID, payloadFolder, folderPath);
        ContentTestHelper.checkJson("rest/set_folder_local_acl_response.json", resultSetAclFolder, DOC_COMPARATOR,
                aclFolderSysId);

        String payloadFile = """
                {
                    "sys_acl": [
                        {
                            "user": {
                                "id" : "%s"
                            },
                            "permission": "ReadWrite",
                            "granted": true,
                            "creator": "%s"
                        }
                    ]
                }
                """.formatted(TEST_ADMIN_USER, TEST_ADMIN_USER);
        String aclFilePath = String.format("%s/%s", PATH, aclFileSysId);
        var resultSetAclFile = checkResponseWithPayload(OK, PUT, REPO_ID, payloadFile, aclFilePath);
        ContentTestHelper.checkJson("rest/set_file_local_acl_response.json", resultSetAclFile, DOC_COMPARATOR,
                aclFileSysId, aclFolderSysId);

        // Block ACL inheritance on file from the folder
        String payloadFileEveryone = """
                {
                    "sys_acl": [
                        {
                            "user": {
                                "id" : "__Everyone__"
                            },
                            "permission": "Everything",
                            "granted": false,
                            "creator": "%s"
                        }
                    ]
                }
                """.formatted(TEST_ADMIN_USER);
        resultSetAclFile = checkResponseWithPayload(OK, PUT, REPO_ID, payloadFileEveryone, aclFilePath);
        ContentTestHelper.checkJson("rest/set_acl_can_block_inheritance_response.json", resultSetAclFile,
                DOC_COMPARATOR, aclFileSysId, aclFolderSysId);
        cleanUpAfterACLTest();
    }

    @Test
    public void testACLWithBlockInheritancePATCH() throws Exception {
        createACLFolderAndFile();
        String payloadFolder = """
                [
                    { "op": "add", "path": "/sys_acl", "value": [
                            {
                                "user": {
                                    "id" : "%s"
                                },
                                "permission": "Read",
                                "granted": true,
                                "creator": "%s"
                            }
                        ]
                    }
                ]
                """.formatted(TEST_ADMIN_USER, TEST_ADMIN_USER);
        String folderPath = String.format("%s/%s", PATH, aclFolderSysId);
        var resultSetAclFolder = checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID,
                payloadFolder, folderPath);
        ContentTestHelper.checkJson("rest/set_folder_local_acl_response.json", resultSetAclFolder, DOC_COMPARATOR,
                aclFolderSysId);

        // Apply Local ACL on file inside the folder
        String payloadFile = """
                [
                    { "op": "add", "path": "/sys_acl", "value": [
                            {
                                "user": {
                                    "id" : "%s"
                                },
                                "permission": "ReadWrite",
                                "granted": true,
                                "creator": "%s"
                            }
                        ]
                    }
                ]
                """.formatted(TEST_ADMIN_USER, TEST_ADMIN_USER);
        String aclFilePath = String.format("%s/%s", PATH, aclFileSysId);
        var responseSetAclFile = checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID,
                payloadFile, aclFilePath);
        ContentTestHelper.checkJson("rest/set_file_local_acl_response.json", responseSetAclFile, DOC_COMPARATOR,
                aclFileSysId, aclFolderSysId);

        // Block ACL inheritance on file from the folder
        String payloadFileEveryone = """
                [
                    { "op": "replace", "path": "/sys_acl/0", "value" :
                        {
                            "user": {
                                "id" : "__Everyone__"
                            },
                            "permission": "Everything",
                            "granted": false,
                            "creator": "%s"
                        }
                    }
                ]
                """.formatted(TEST_ADMIN_USER);
        responseSetAclFile = checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID,
                payloadFileEveryone, aclFilePath);
        ContentTestHelper.checkJson("rest/set_acl_can_block_inheritance_response.json", responseSetAclFile,
                DOC_COMPARATOR, aclFileSysId, aclFolderSysId);
        cleanUpAfterACLTest();
    }

    @Test
    public void testACLWithDatesPATCH() throws Exception {
        createACLFolderAndFile();
        String payloadFolder = """
                [
                    { "op": "add", "path": "/sys_acl", "value": [
                            {
                                "user": {
                                    "id" : "%s"
                                },
                                "permission": "Read",
                                "granted": true,
                                "creator": "%s",
                                "begin": "2022-01-15T13:05:15Z",
                                "end": "2022-02-28T05:13:19Z"
                            }
                        ]
                    }
                ]
                """.formatted(TEST_ADMIN_USER, TEST_ADMIN_USER);
        String folderPath = String.format("%s/%s", PATH, aclFolderSysId);
        checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, payloadFolder,
                folderPath).andExpectAll(jsonPath(SYS_ACL + "[0].begin").value("2022-01-15T13:05:15Z"),
                jsonPath(SYS_ACL + "[0].end").value("2022-02-28T05:13:19Z"),
                jsonPath(SYS_ACL + "[0].status").value("ARCHIVED"));
        cleanUpAfterACLTest();
    }

    @Test
    public void testMarkAsRecordWithRetentionForGovernableDocumentTypePATCH() throws Exception{
        createRetentionFolderAndFile(getCreateGovernableFileAsNonRecordJson());
        String retainUntil = Instant.now().plusSeconds(2).toString();
        String payloadRecord = """
                [
                    { "op": "add", "path": "/sysgov_isRecord", "value": true },
                    {
                       "op": "add",
                       "path": "/sysgov_retainUntil",
                       "value" : "%s"
                     }
                ]
                """.formatted(retainUntil);
        String filePath = String.format("%s/%s", PATH, aclFileSysId);
        checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON,
                REPO_ID, payloadRecord, filePath).andExpectAll(jsonPath(SYSGOV_IS_RECORD).value("true"),
                jsonPath(SYSGOV_RETAIN_UNTIL).value(retainUntil));
        //checks is delete is forbidden
        tryCleanupRecordsAfterRetentionTest();
        String resetRecord = """
                [
                    { "op": "add", "path": "/sysgov_isRecord", "value": false }
                ]
                """;
        //checks is update is forbidden
        checkResponseWithPayload(FORBIDDEN, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON,
                REPO_ID, resetRecord, filePath);
        //need to wait more than 2 seconds to allow retention to expire
        Thread.sleep(3000);
        //checks is update is allowed
        checkResponseWithPayload(BAD_REQUEST, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON,
                REPO_ID, resetRecord, filePath);
        //checks is delete is allowed
        tryCleanupNonRecordsAfterRetentionTest();
    }

    @Test
    public void testCreateDocumentWithIsRecordAsStringInsteadOfBoolean() throws Exception {
        String payload = getCreateGovernableFileAsRecordJson("\"true\""); // string, gets converted
        checkResponseWithPayload(OK, POST, REPO_ID, payload, getRootApiPath());
    }

    @Test
    public void testCreateDocumentWithIsRecordInvalidValue() throws Exception {
        String payload = getCreateGovernableFileAsRecordJson("\"foobar\""); // invalid value
        checkResponseWithPayload(BAD_REQUEST, POST, REPO_ID, payload, getRootApiPath());
    }

    @Test
    public void testCreateDocumentWithRetainUntilInvalidValue() throws Exception {
        createRetentionFolderAndFile(getCreateGovernableFileAsRecordJson());
        String retainUntil = "foobar"; // invalid value
        String filePath = String.format("%s/%s", PATH, aclFileSysId);
        checkResponseWithPayload(BAD_REQUEST, PUT, APPLICATION_JSON, REPO_ID, setRetainUntilJson(retainUntil),
                filePath);
    }

    @Test
    public void testCreateDocumentWithRetentionForGovernableDocumentTypePOST() throws Exception {
        createRetentionFolderAndFile(getCreateGovernableFileAsRecordJson());
        String retainUntil = Instant.now().plusSeconds(10).toString();
        String filePath = String.format("%s/%s", PATH, aclFileSysId);
        checkResponseWithPayload(OK, PUT, APPLICATION_JSON,
                REPO_ID, setRetainUntilJson(retainUntil), filePath).andExpectAll(jsonPath(SYSGOV_IS_RECORD).value("true"),
                jsonPath("sysgov_retainUntil").value(retainUntil));

        checkResponseWithPayload(OK, PUT, APPLICATION_JSON,
                REPO_ID, setLegalHoldJson(true), filePath).andExpectAll(jsonPath(SYSGOV_IS_RECORD).value("true"),
                jsonPath(SYSGOV_HAS_LEGAL_HOLD).value("true"));
        checkResponseWithPayload(OK, PUT, APPLICATION_JSON,
                REPO_ID, setLegalHoldJson(false), filePath).andExpectAll(jsonPath(SYSGOV_IS_RECORD).value("true"),
                jsonPath(SYSGOV_HAS_LEGAL_HOLD).value("false"));

    }

    @Test
    public void testCreateDocumentWithRetentionForNonGovernableDocumentTypePOST() throws Exception {
        createRetentionFolderAndFile(getCreateNonGovernableFileJson());
        String retainUntil = Instant.now().plusSeconds(10).toString();
        String filePath = String.format("%s/%s", PATH, aclFileSysId);
        checkResponseWithPayload(OK, PUT, APPLICATION_JSON, REPO_ID, setRetainUntilJson(retainUntil), filePath);

        checkResponseWithPayload(OK, PUT, APPLICATION_JSON, REPO_ID, setLegalHoldJson(true), filePath);
        //checks is delete is allowed
        tryCleanupNonRecordsAfterRetentionTest();
    }

    @Test
    public void testCreateDocumentWithRetentionButWithoutMarkedAsRecordForGovernableDocumentTypePOST() throws Exception {
        createRetentionFolderAndFile(getCreateGovernableFileAsNonRecordJson());
        String retainUntil = Instant.now().plusSeconds(5).toString();
        String filePath = String.format("%s/%s", PATH, aclFileSysId);
        checkResponseWithPayload(OK, PUT, APPLICATION_JSON,
                REPO_ID, setRetainUntilJson(retainUntil), filePath).andExpectAll(jsonPath(SYSGOV_IS_RECORD).value("false"));
    }


    @Test
    public void testMarkAsRecordWithRetentionForGovernableDocumentTypePUT() throws Exception {
        createAndTestGovernableFileWithRetention(Instant.now().plusSeconds(5).toString());
        tryCleanupRecordsAfterRetentionTest();
    }

    @Test
    public void testMarkAsRecordWithIndeterminateRetentionForGovernableDocumentTypePUT() throws Exception {
        String filePath = createAndTestGovernableFileWithRetention(RETAIN_UNTIL_INDETERMINATE_ISO);
        String adjustedRetainUntil = Instant.now().plusSeconds(100).toString();
        checkResponseWithPayload(OK, PUT, APPLICATION_JSON,
                REPO_ID, getUpdateIndeterminateRetention(adjustedRetainUntil), filePath).andExpectAll(jsonPath(SYSGOV_IS_RECORD).value("true"),
                jsonPath(SYSGOV_RETAIN_UNTIL).value(adjustedRetainUntil));
        tryCleanupRecordsAfterRetentionTest();
    }

    private @NotNull String createAndTestGovernableFileWithRetention(String retainUntil) throws Exception {
        createRetentionFolderAndFile(getCreateGovernableFileAsNonRecordJson());
        String payloadRecord = getCreateGovernableFileWithRetentionJson(retainUntil);
        String filePath = String.format("%s/%s", PATH, aclFileSysId);
        checkResponseWithPayload(OK, PUT, APPLICATION_JSON,
                REPO_ID, payloadRecord, filePath).andExpectAll(jsonPath(SYSGOV_IS_RECORD).value("true"),
                jsonPath(SYSGOV_RETAIN_UNTIL).value(retainUntil));
        return filePath;
    }

    @Test
    public void testUnmarkRecordForGovernableDocumentTypePUT() throws Exception {
        createRetentionFolderAndFile(getCreateGovernableFileAsNonRecordJson());
        String payloadRecord = """
                {
                  "sysgov_isRecord": true
                }
                """;
        String filePath = String.format("%s/%s", PATH, aclFileSysId);
        checkResponseWithPayload(OK, PUT, APPLICATION_JSON,
                REPO_ID, payloadRecord, filePath).andExpectAll(jsonPath(SYSGOV_IS_RECORD).value("true"));
        //Unmark record
        String payloadUnmarkRecord = """
                {
                  "sysgov_isRecord": false
                }
                """;
        checkResponseWithPayload(BAD_REQUEST, PUT, APPLICATION_JSON,
                REPO_ID, payloadUnmarkRecord, filePath);

        tryCleanupNonRecordsAfterRetentionTest();
    }

    @Test
    public void testSetUnsetOnLegalHoldForGovernableDocumentTypePUT() throws Exception {
        createRetentionFolderAndFile(getCreateGovernableFileAsNonRecordJson());
        String payloadRecord = """
                {
                  "sysgov_isRecord": true,
                  "sysgov_hasLegalHold": true
                }
                """;
        String filePath = String.format("%s/%s", PATH, aclFileSysId);
        checkResponseWithPayload(OK, PUT, APPLICATION_JSON,
                REPO_ID, payloadRecord, filePath).andExpectAll(jsonPath(SYSGOV_IS_RECORD).value("true"),
                jsonPath(SYSGOV_HAS_LEGAL_HOLD).value(true));
        //checks is delete is forbidden
        tryCleanupRecordsAfterRetentionTest();

        String updateRecord = """
                {
                  "sys_description": "Updated description"
                }
                """;
        // checks if update is forbidden
        checkResponseWithPayload(FORBIDDEN, PUT, MediaType.APPLICATION_JSON,
                REPO_ID, updateRecord, filePath);

        // clear on hold
        checkResponseWithPayload(OK, PUT, MediaType.APPLICATION_JSON,
                REPO_ID, setLegalHoldJson(false), filePath);
        // checks again if update is forbidden
        checkResponseWithPayload(OK, PUT, MediaType.APPLICATION_JSON,
                REPO_ID, updateRecord, filePath);
        // checks is delete is allowed
        tryCleanupNonRecordsAfterRetentionTest();
    }

    @Test
    public void testUnsetOnLegalHoldWithOtherPropertiesForGovernableDocumentTypePUT() throws Exception {
        createRetentionFolderAndFile(getCreateGovernableFileAsNonRecordJson());
        String payloadRecord = """
                {
                  "sysgov_isRecord": true,
                  "sysgov_hasLegalHold": true
                }
                """;
        String filePath = String.format("%s/%s", PATH, aclFileSysId);
        checkResponseWithPayload(OK, PUT, APPLICATION_JSON,
                REPO_ID, payloadRecord, filePath).andExpectAll(jsonPath(SYSGOV_IS_RECORD).value("true"),
                jsonPath(SYSGOV_HAS_LEGAL_HOLD).value(true));
        //checks is delete is forbidden
        tryCleanupRecordsAfterRetentionTest();

        String updateRecord = """
                {
                  "sys_description": "Updated description"
                }
                """;
        // checks if update is forbidden
        checkResponseWithPayload(FORBIDDEN, PUT, MediaType.APPLICATION_JSON,
                REPO_ID, updateRecord, filePath);

        // clear on hold
        checkResponseWithPayload(OK, PUT, MediaType.APPLICATION_JSON,
                REPO_ID, setLegalHoldWithOtherPropertiesJson(false), filePath);
        // check that update is now allowed
        checkResponseWithPayload(OK, PUT, MediaType.APPLICATION_JSON,
                REPO_ID, updateRecord, filePath);
        // checks is delete is allowed
        tryCleanupNonRecordsAfterRetentionTest();
    }

    @Test
    @Disabled("This test is failing due to patch method for updating retention is not working")
    public void testSetUnsetOnLegalHoldForGovernableDocumentTypePATCH() throws Exception {
        createRetentionFolderAndFile(getCreateGovernableFileAsNonRecordJson());
        String payloadRecord = """
                [
                    {
                       "op": "add",
                       "path": "/sysgov_isRecord",
                       "value" : true
                     },
                     {
                       "op": "add",
                       "path": "/sysgov_hasLegalHold",
                       "value" : true
                     }
                ]
                """;
        String filePath = String.format("%s/%s", PATH, aclFileSysId);
        checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON,
                REPO_ID, payloadRecord, filePath).andExpectAll(jsonPath(SYSGOV_IS_RECORD).value("true"),
                jsonPath(SYSGOV_HAS_LEGAL_HOLD).value(true));
        // checks if delete is forbidden
        tryCleanupRecordsAfterRetentionTest();

        String updateRecord = """
                {
                  "sys_description": "Updated description"
                }
                """;
        // checks if update is forbidden
        checkResponseWithPayload(FORBIDDEN, PUT, MediaType.APPLICATION_JSON,
                REPO_ID, updateRecord, filePath);

        String resetRecord = """
                [
                    {
                       "op": "add",
                       "path": "/sysgov_hasLegalHold",
                       "value" : false
                     }
                ]
                """;

        // clear on hold
        checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON,
                REPO_ID, resetRecord, filePath);
        // checks again if update is forbidden
        checkResponseWithPayload(OK, PUT, MediaType.APPLICATION_JSON,
                REPO_ID, updateRecord, filePath);
        // checks if delete is allowed
        tryCleanupNonRecordsAfterRetentionTest();
    }

    @Test
    public void testSetOnLegalHoldForNonRecordForGovernableDocumentTypePUT() throws Exception {
        createRetentionFolderAndFile(getCreateGovernableFileAsNonRecordJson());
        String filePath = String.format("%s/%s", PATH, aclFileSysId);
        checkResponseWithPayload(OK, PUT, APPLICATION_JSON,
                REPO_ID, setLegalHoldJson(true), filePath).andExpectAll(jsonPath(SYSGOV_IS_RECORD).value("false"),
                jsonPath(SYSGOV_HAS_LEGAL_HOLD).value("false"));
        // checks if delete is allowed
        tryCleanupNonRecordsAfterRetentionTest();
    }

    @Test
    public void testExtendRetentionPeriodForGovernableDocumentTypePUT() throws Exception {
        createRetentionFolderAndFile(getCreateGovernableFileAsRecordJson());
        String retainUntil = Instant.now().plusSeconds(5).toString();
        String filePath = String.format("%s/%s", PATH, aclFileSysId);
        checkResponseWithPayload(OK, PUT, APPLICATION_JSON,
                REPO_ID, setRetainUntilJson(retainUntil), filePath).andExpectAll(jsonPath(SYSGOV_IS_RECORD).value("true"),
                jsonPath(SYSGOV_RETAIN_UNTIL).value(retainUntil));

        // extend retention period
        retainUntil = Instant.now().plusSeconds(10).toString();
        checkResponseWithPayload(OK, PUT, APPLICATION_JSON,
                REPO_ID, setRetainUntilJson(retainUntil), filePath).andExpectAll(jsonPath(SYSGOV_IS_RECORD).value("true"),
                jsonPath(SYSGOV_RETAIN_UNTIL).value(retainUntil));

        // checks if delete is allowed
        tryCleanupRecordsAfterRetentionTest();
    }

    @Test
    public void testSetRetentionPeriodWithManageRetentionPermissionNonAdminForGovernableDocumentTypePUT() throws Exception {
        createRetentionFolderAndFile(getCreateGovernableFileAsRecordJson());
        String payloadFolder = """
                {
                    "sys_acl": [
                        {
                            "user": {
                                "id" : "%s"
                            },
                            "permission": "Read",
                            "granted": true
                        }
                    ]
                }
                """.formatted(ALT_USER);
        String folderPath = String.format("%s/%s", PATH, aclFolderSysId);
        checkResponseWithPayload(OK, PUT, APPLICATION_JSON, REPO_ID, payloadFolder, folderPath);
        String payloadFile = """
                {
                    "sys_acl": [
                        {
                            "user": {
                                "id" : "%s"
                            },
                            "permission": "ManageRetention",
                            "granted": true
                        },
                        {
                            "user": {
                                "id" : "%s"
                            },
                            "permission": "Read",
                            "granted": true
                        }
                    ]
                }
                """.formatted(ALT_USER, ALT_ANOTHER_USER);
        String filePath = String.format("%s/%s", PATH, aclFileSysId);
        checkResponseWithPayload(OK, PUT, APPLICATION_JSON, REPO_ID, payloadFile, filePath);

        String retainUntil = Instant.now().plusSeconds(10).toString();
        checkResponseWithPayloadWithUser(OK, PUT, APPLICATION_JSON, REPO_ID, setRetainUntilJson(retainUntil), filePath, ALT_USER);
        checkResponseWithPayloadWithUser(FORBIDDEN, PUT, APPLICATION_JSON, REPO_ID, setRetainUntilJson(retainUntil), filePath, ALT_ANOTHER_USER);
    }

    @Test
    public void testExtendRetentionWithManageRetentionPermissionNonAdminForGovernableDocumentTypePUT() throws Exception {
        createRetentionFolderAndFile(getCreateGovernableFileAsRecordJson());
        String folderPath = String.format("%s/%s", PATH, aclFolderSysId);
        checkResponseWithPayload(OK, PUT, APPLICATION_JSON, REPO_ID, getReadAClJson(ALT_USER), folderPath);
        String payloadFile = """
                {
                    "sys_acl": [
                        {
                            "user": {
                                "id" : "%s"
                            },
                            "permission": "ManageRetention",
                            "granted": true
                        },
                        {
                            "user": {
                                "id" : "%s"
                            },
                            "permission": "Read",
                            "granted": true
                        }
                    ]
                }
                """.formatted(ALT_USER, ALT_ANOTHER_USER);
        String filePath = String.format("%s/%s", PATH, aclFileSysId);
        checkResponseWithPayload(OK, PUT, APPLICATION_JSON, REPO_ID, payloadFile, filePath);

        Instant currentTime = Instant.now();
        String retainUntil = currentTime.plusSeconds(10).toString();
        checkResponseWithPayloadWithUser(OK, PUT, APPLICATION_JSON, REPO_ID, setRetainUntilJson(retainUntil), filePath, ALT_USER);
        checkResponseWithPayloadWithUser(FORBIDDEN, PUT, APPLICATION_JSON, REPO_ID, setRetainUntilJson(retainUntil), filePath, ALT_ANOTHER_USER);

        String retainUntilExtended = currentTime.plusSeconds(15).toString();
        checkResponseWithPayloadWithUser(OK, PUT, APPLICATION_JSON,
                REPO_ID, setRetainUntilJson(retainUntilExtended), filePath, ALT_USER).andExpectAll(jsonPath(SYSGOV_IS_RECORD).value("true"),
                jsonPath(SYSGOV_RETAIN_UNTIL).value(retainUntilExtended));
    }

    @Test
    public void testAdjustRetentionForNonAdminForGovernableDocumentTypePUT() throws Exception {
        createRetentionFolderAndFile(getCreateGovernableFileAsRecordJson());
        String folderPath = String.format("%s/%s", PATH, aclFolderSysId);
        checkResponseWithPayload(OK, PUT, APPLICATION_JSON, REPO_ID, getReadAClJson(ALT_USER_ID), folderPath);
        String payloadFile = """
                {
                    "sys_acl": [
                        {
                            "user": {
                                "id" : "%s"
                            },
                            "permission": "Read",
                            "granted": true
                        },
                        {
                            "user": {
                                "id" : "%s"
                            },
                            "permission": "Write",
                            "granted": true
                        }
                    ]
                }
                """.formatted(ALT_USER_ID, ALT_USER_ID);
        String filePath = String.format("%s/%s", PATH, aclFileSysId);
        checkResponseWithPayload(OK, PUT, APPLICATION_JSON, REPO_ID, payloadFile, filePath);

        checkResponseWithPayloadWithUser(OK, PUT, APPLICATION_JSON, REPO_ID, setRetainUntilJson(RETAIN_UNTIL_INDETERMINATE_ISO), filePath,
                TEST_ADMIN_USER);
        checkResponseWithPayloadWithUser(OK, GET, "", REPO_ID, null, filePath, ALT_USER_ID);

        String adjustedRetainUntil = Instant.now().plusSeconds(100).toString();
        checkResponseWithPayloadWithUser(FORBIDDEN, PUT, APPLICATION_JSON,
                REPO_ID, getUpdateIndeterminateRetention(adjustedRetainUntil), filePath, ALT_USER_ID);
        checkResponseWithPayloadWithUser(OK, PUT, APPLICATION_JSON,
                REPO_ID, getUpdateIndeterminateRetention(adjustedRetainUntil), filePath,
                TEST_ADMIN_USER).andExpectAll(jsonPath(SYSGOV_IS_RECORD).value("true"),
                jsonPath(SYSGOV_RETAIN_UNTIL).value(adjustedRetainUntil));
    }

    @Test
    public void testSetLegalHoldWithManageRetentionPermissionNonAdminForGovernableDocumentTypePUT() throws Exception {
        createRetentionFolderAndFile(getCreateGovernableFileAsRecordJson());
        UserPrincipal myOtherUser = DummyLoginFeature.getTestPrincipal("myOtherUser", false);
        String payloadFolder = """
                {
                    "sys_acl": [
                        {
                            "user": {
                                "id" : "%s"
                            },
                            "permission": "Read",
                            "granted": true
                        }
                    ]
                }
                """.formatted(ALT_USER);
        String folderPath = String.format("%s/%s", PATH, aclFolderSysId);
        checkResponseWithPayload(OK, PUT, APPLICATION_JSON, REPO_ID, payloadFolder, folderPath);
        String payloadFile = """
                {
                    "sys_acl": [
                        {
                            "user": {
                                "id" : "%s"
                            },
                            "permission": "ManageRetention",
                            "granted": true
                        },
                        {
                            "user": {
                                "id" : "%s"
                            },
                            "permission": "Read",
                            "granted": true
                        }
                    ]
                }
                """.formatted(ALT_USER, ALT_ANOTHER_USER);
        String filePath = String.format("%s/%s", PATH, aclFileSysId);
        checkResponseWithPayload(OK, PUT, APPLICATION_JSON, REPO_ID, payloadFile, filePath);

        checkResponseWithPayloadWithUser(OK, PUT, APPLICATION_JSON, REPO_ID, setLegalHoldJson(true), filePath, ALT_USER)
                .andExpectAll(jsonPath(SYSGOV_IS_RECORD).value("true"),
                jsonPath(SYSGOV_HAS_LEGAL_HOLD).value(true));;
        checkResponseWithPayloadWithUser(FORBIDDEN, PUT, APPLICATION_JSON, REPO_ID, setLegalHoldJson(true), filePath, ALT_ANOTHER_USER);

        checkResponseWithPayloadWithUser(OK, PUT, APPLICATION_JSON, REPO_ID, setLegalHoldJson(false), filePath, ALT_USER)
                .andExpectAll(jsonPath(SYSGOV_IS_RECORD).value("true"),
                jsonPath(SYSGOV_HAS_LEGAL_HOLD).value(false));;
        checkResponseWithPayloadWithUser(FORBIDDEN, PUT, APPLICATION_JSON, REPO_ID, setLegalHoldJson(false), filePath, ALT_ANOTHER_USER);
    }

    @Test
    public void testShortenRetentionPeriodForGovernableDocumentTypePUT() throws Exception {
        createRetentionFolderAndFile(getCreateGovernableFileAsRecordJson());
        Instant currentTime = Instant.now();
        String retainUntilLonger = currentTime.plusSeconds(10).toString();
        String filePath = String.format("%s/%s", PATH, aclFileSysId);
        checkResponseWithPayload(OK, PUT, APPLICATION_JSON,
                REPO_ID, setRetainUntilJson(retainUntilLonger), filePath).andExpectAll(jsonPath(SYSGOV_IS_RECORD).value("true"),
                jsonPath(SYSGOV_RETAIN_UNTIL).value(retainUntilLonger));

        // shorten retention period, should not be allowed
        String retainUntilShorter = Instant.now().plusSeconds(5).toString();
        checkResponseWithPayload(BAD_REQUEST, PUT, APPLICATION_JSON,
                REPO_ID, setRetainUntilJson(retainUntilShorter), filePath);

        // checks if delete is allowed
        tryCleanupRecordsAfterRetentionTest();
    }

    @Test
    @Disabled("This test is failing due to patch method for updating retention is not working")
    public void testExtendRetentionPeriodForGovernableDocumentTypePATCH() throws Exception {
        createRetentionFolderAndFile(getCreateGovernableFileAsRecordJson());
        String retainUntil = Instant.now().plusSeconds(5).toString();
        String payloadRecord = """
                [
                    {
                       "op": "add",
                       "path": "/sysgov_retainUntil",
                       "value" : "%s"
                     }
                ]
                """.formatted(retainUntil);
        String filePath = String.format("%s/%s", PATH, aclFileSysId);
        checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON,
                REPO_ID, payloadRecord, filePath).andExpectAll(jsonPath(SYSGOV_IS_RECORD).value("true"),
                jsonPath(SYSGOV_RETAIN_UNTIL).value(retainUntil));

        // extend retention period
        retainUntil = Instant.now().plusSeconds(10).toString();
        String payloadRecordExtended = """
                [
                    {
                       "op": "add",
                       "path": "/sysgov_retainUntil",
                       "value" : "%s"
                     }
                ]
                """.formatted(retainUntil);
        checkResponseWithPayload(OK, PATCH, APPLICATION_JSON_PATCH_JSON,
                REPO_ID, payloadRecordExtended, filePath).andExpectAll(jsonPath(SYSGOV_IS_RECORD).value("true"),
                jsonPath(SYSGOV_RETAIN_UNTIL).value(retainUntil));

        // checks if delete is allowed
        tryCleanupRecordsAfterRetentionTest();
    }


    @Test
    public void testACLWithNullDatesPATCH() throws Exception {
        createACLFolderAndFile();
        String payloadFolder = """
                [
                    { "op": "add", "path": "/sys_acl", "value": [
                            {
                                "user": {
                                    "id" : "TestAdministrator"
                                },
                                "permission": "Read",
                                "granted": true,
                                "creator": "TestAdministrator",
                                "begin": null,
                                "end": null
                            }
                        ]
                    }
                ]
                """;
        String folderPath = String.format("%s/%s", PATH, aclFolderSysId);
        checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON, REPO_ID, payloadFolder,
                folderPath).andExpectAll(jsonPath(SYS_ACL + "[0].begin").doesNotExist(),
                jsonPath(SYS_ACL + "[0].end").doesNotExist());
        cleanUpAfterACLTest();
    }

    @Test
    public void testACLWithBeginDateAfterEndDatePATCH() throws Exception {
        createACLFolderAndFile();
        String payloadFolder = """
                [
                    { "op": "add", "path": "/sys_acl", "value": [
                            {
                                "user": {
                                    "id" : "TestAdministrator"
                                },
                                "permission": "Read",
                                "granted": true,
                                "creator": "TestAdministrator",
                                "begin": "2022-02-28T05:13:19Z",
                                "end": "2022-01-15T13:05:15Z"
                            }
                        ]
                    }
                ]
                """;
        String folderPath = String.format("%s/%s", PATH, aclFolderSysId);
        var responseSetAclFolder = checkResponseWithPayload(BAD_REQUEST, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON,
                REPO_ID, payloadFolder, folderPath);
        checkErrorResponse(BAD_REQUEST, "'begin' date cannot be after 'end' date", responseSetAclFolder);
        cleanUpAfterACLTest();
    }

    @Test
    public void testACLWithInvalidDatePUT() throws Exception {
        createACLFolderAndFile();
        String payloadFolder = """
                {
                    "sys_acl": [
                        {
                            "user": {
                                "id" : "TestAdministrator"
                            },
                            "permission": "Read",
                            "granted": true,
                            "creator": "TestAdministrator",
                            "begin": "2022-02-28T05:13:19Z",
                            "end": "Fri Mar 25 08:41:55 GMT 2022"
                        }
                    ]
                }
                """;
        String folderPath = String.format("%s/%s", PATH, aclFolderSysId);
        var responseSetAclFolder = checkResponseWithPayload(BAD_REQUEST, PUT, REPO_ID, payloadFolder, folderPath);
        checkErrorResponse(BAD_REQUEST, "Invalid date format for 'end' date", responseSetAclFolder);
        cleanUpAfterACLTest();
    }

    @Test
    public void testSetSecurityPolicies() throws Exception {
        setCustomSecurityPolicy();
        try {
            doc = coreSession.createDocumentModel("/", "file", SYS_FILE);
            doc.create();
            coreSession.save();
            String id = doc.getId();
            String path = doc.getPathAsString();

            String payload = """
                    {
                        "sys_secpol": ["mypolicy"]
                    }
                    """;
            var result = checkResponseWithPayload(OK, PUT, REPO_ID, payload, getApiPath(id, path));
            checkResponseBodyReplacing("rest/doc_secpol_updated.json", id, "file", result);
        } finally {
            resetSecurityPolicies();
        }
    }

    private void setCustomSecurityPolicy() {
        // default config
        SecurityService securityService = Framework.getService(SecurityService.class);
        var config = securityService.getSecurityPolicyService(coreSession.getRepositoryName())
                                    .getSecurityPolicyConfigMap();

        // add custom security policy config
        String name = "mypolicy";
        String mixin = "CustomFacet";
        String attribute = "clearance";
        // custom security policy
        Set<String> permissions = Set.of(EVERYTHING);
        SecurityPolicyConfig custom = new SecurityLevelSecurityPolicyConfig(null, null, permissions, mixin, attribute);
        config.put(name, custom);

        securityService.setSecurityPolicies(coreSession.getRepositoryName(), config);
    }

    private void resetSecurityPolicies() {
        SecurityService securityService = Framework.getService(SecurityService.class);
        var config = securityService.getSecurityPolicyService(coreSession.getRepositoryName())
                                    .getSecurityPolicyConfigMap();
        config.remove("mypolicy");
        securityService.setSecurityPolicies(coreSession.getRepositoryName(), config);
    }

    @Test
    public void testCreatePreventsDuplicateWithEnforceSysNameTrue() throws Exception {
        String creationJson = """
                {
                "sys_primaryType": "SysFile",
                "sys_name": "myfile"
              }
              """;
        var result = checkResponseWithPayload(OK, POST, REPO_ID, creationJson, getRootApiPath());
        result.andExpect(jsonPath(SYS_NAME).value("myfile"));

        // again with the same name, will automatically use a different sys_name
        result = checkResponseWithPayload(OK, POST, REPO_ID, creationJson, getRootApiPath());
        result.andExpect(jsonPath(SYS_NAME).value(startsWith("myfile.")));

        // again with the same name, passing enforceSysName=true, to cause a CONFLICT
        var conflict = checkResponseWithPayload(CONFLICT, POST, REPO_ID, creationJson,
                getRootApiPath() + "?" + ENFORCE_SYS_NAME + "=true");
        // the 409 this operation declares is this one, so pin the response a client actually reads: both
        // create operations declare it, and both run this test through their own subclass
        ContentTestHelper.checkErrorResponse(CONFLICT, "Duplicate name in parent: myfile", conflict);
    }

}
