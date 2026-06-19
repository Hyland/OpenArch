/*
 * (C) Copyright 2022 Hyland (https://hyland.com/) and others.
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
 *     Adam Dunajewski
 */
package org.nuxeo.rest.document.test;

import static jakarta.ws.rs.core.Response.Status.NO_CONTENT;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ID;
import static org.nuxeo.rest.document.AbstractDocumentAPI.PATH;
import static org.nuxeo.rest.document.test.ContentTestHelper.getJsonPath;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

import jakarta.ws.rs.core.MediaType;

import org.junit.jupiter.api.Test;
import org.nuxeo.rest.document.DocumentAPI;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(classes = { DocumentAPI.class })
@Import(DocumentAPITestConfiguration.class)
public class AclBlockInheritanceMultipleAcesTest extends AbstractAPITest {

    private String aclFolderSysId;

    private String aclFileSysId;

    @Test
    public void testBlockInheritanceAndSetACLWithMultiACEsPUT() throws Exception {
        createACLFolderAndFile();
        String payload = """
                {
                    "sys_acl": [
                        {
                            "user" : {
                                "id": "ff7b0360-3594-416c-b587-2fa4dd0839ce"
                            },
                            "permission": "ReadWrite",
                            "granted": true
                        },
                        {
                            "group": {
                                "name": "members",
                                "id": "8d6542c9-50e9-4027-aa10-2cfb2179413f"
                            },
                            "permission": "Read",
                            "granted": true
                        },
                        {
                            "user": {
                                "id": "__Everyone__"
                            },
                            "permission": "Everything",
                            "granted": false
                        }
                    ]
                }
                """;
        String aclFilePath = String.format("%s/%s", PATH, aclFileSysId);
        var responseBlockInheritance = checkResponseWithPayload(OK, PUT, REPO_ID, payload, aclFilePath);
        ContentTestHelper.checkJson("rest/block_inheritance_set_acl_multiple_aces_payload_response.json",
                responseBlockInheritance, DOC_COMPARATOR, aclFileSysId, aclFolderSysId);

        cleanUpAfterACLTest(String.format("%s/%s", PATH, aclFolderSysId), aclFilePath);
    }

    @Test
    public void testBlockInheritanceAndSetACLWithMultiACEsPATCH() throws Exception {
        createACLFolderAndFile();
        String payload = """
                [
                    {
                    "op": "add", "path": "/sys_acl", "value":
                        [
                            {
                                "user" : {
                                    "id": "ff7b0360-3594-416c-b587-2fa4dd0839ce"
                                },
                                "permission": "ReadWrite",
                                "granted": true
                            },
                            {
                                "group": {
                                    "name": "members",
                                    "id": "8d6542c9-50e9-4027-aa10-2cfb2179413f"
                                },
                                "permission": "Read",
                                "granted": true
                            },
                            {
                                "user": {
                                    "id": "__Everyone__"
                                },
                                "permission": "Everything",
                                "granted": false
                            }
                        ]
                    }
                ]
                """;
        String aclFilePath = String.format("%s/%s", PATH, aclFileSysId);
        var responseBlockInheritance = checkResponseWithPayload(OK, PATCH, MediaType.APPLICATION_JSON_PATCH_JSON,
                REPO_ID, payload, aclFilePath);
        ContentTestHelper.checkJson("rest/block_inheritance_set_acl_multiple_aces_payload_response.json",
                responseBlockInheritance, DOC_COMPARATOR, aclFileSysId, aclFolderSysId);

        cleanUpAfterACLTest(String.format("%s/%s", PATH, aclFolderSysId), aclFilePath);
    }

    protected String getCreateFolderJsonForDocumentACLUpdate() {
        return """
                {
                    "sys_primaryType": "SysFolder",
                    "sys_title": "My ACL Folder",
                    "sys_name": "My ACL Folder Name"
                }
                """;
    }

    protected String getCreateFileJsonForAclBlockInheritance() {
        return """
                {
                    "sys_primaryType": "SysFile",
                    "sys_title": "My File",
                    "sys_name": "My File Name"
                }
                """;
    }

    protected void createACLFolderAndFile() throws Exception {
        var folderResponse = checkResponseWithPayload(OK, POST, REPO_ID, getCreateFolderJsonForDocumentACLUpdate(),
                getRootApiPath());
        aclFolderSysId = getJsonPath(folderResponse, SYS_ID);
        String folderPath = String.format("%s/%s", PATH, aclFolderSysId);
        var responseSetAclFile = checkResponseWithPayload(OK, POST, REPO_ID, getCreateFileJsonForAclBlockInheritance(),
                folderPath);
        aclFileSysId = getJsonPath(responseSetAclFile, SYS_ID);
    }

    protected void cleanUpAfterACLTest(String aclFolderPath, String aclFilePath) throws Exception {
        checkResponseWithPayload(NO_CONTENT, DELETE, REPO_ID, "", aclFilePath);
        checkResponseWithPayload(NO_CONTENT, DELETE, REPO_ID, "", aclFolderPath);
    }

    protected String getRootApiPath() {
        return String.format("%s/%s", PATH, ROOT_ID);
    }
}
