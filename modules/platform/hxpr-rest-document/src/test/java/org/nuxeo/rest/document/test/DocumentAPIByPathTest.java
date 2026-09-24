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
package org.nuxeo.rest.document.test;

import static jakarta.ws.rs.core.Response.Status.OK;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_NAME;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_PATH;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_TITLE;
import static org.nuxeo.rest.document.AbstractDocumentAPI.PATH;
import static org.nuxeo.rest.document.test.ContentTestHelper.getJsonPath;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.nuxeo.rest.document.DocumentAPI;
import org.nuxeo.rest.document.DocumentMultipartController;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = { DocumentAPI.class, DocumentMultipartController.class })
public class DocumentAPIByPathTest extends AbstractDocumentAPITest {

    @Override
    protected String getRootApiPath() {
        return String.format("%s/path/", PATH);
    }

    @Override
    protected String getApiPath(String id, String path) {
        return String.format("%s/path%s", PATH, (path.startsWith("/") ? "" : "/") + path);
    }

    @Test
    public void testGetByPathEmpty() throws Exception {
        var result = checkResponse(OK, GET, REPO_ID, getApiPath("", ""));
        ContentTestHelper.checkJson("rest/root.json", result);
    }

    @Test
    public void docShouldGetNameFromSysNameWhenDefined() throws Exception {
        String defaultSysName = "My_Sys_Name";
        String creationFolder = """
                {
                  "sys_title": "My Sys Title",
                  "sys_name": "%s",
                  "sys_description": "Folder with both sys_name and sys_title defined",
                  "sys_primaryType": "SysFolder"
                }
                """.formatted(defaultSysName);
        checkResponseWithPayload(OK, POST, REPO_ID, creationFolder, getRootApiPath()).andExpectAll(
                jsonPath(SYS_TITLE).value("My Sys Title"), jsonPath(SYS_NAME).value(defaultSysName),
                jsonPath(SYS_PATH).value("/" + defaultSysName));

        String filePath = getApiPath(defaultSysName, defaultSysName);
        checkResponse(OK, GET, REPO_ID, filePath).andExpectAll(jsonPath(SYS_TITLE).value("My Sys Title"),
                jsonPath(SYS_NAME).value(defaultSysName), jsonPath(SYS_PATH).value("/" + defaultSysName));
    }

    @Test
    public void docShouldGetNameFromSysTitleWhenSysNameNotDefined() throws Exception {
        String defaultSysTitle = "My Sys Title";
        String creationFolder = """
                {
                  "sys_title": "%s",
                  "sys_description": "Folder with only sys_title defined",
                  "sys_primaryType": "SysFolder"
                }
                """.formatted(defaultSysTitle);
        checkResponseWithPayload(OK, POST, REPO_ID, creationFolder, getRootApiPath()).andExpectAll(
                jsonPath(SYS_TITLE).value(defaultSysTitle), jsonPath(SYS_NAME).value(defaultSysTitle),
                jsonPath(SYS_PATH).value("/" + defaultSysTitle));

        String filePath = getApiPath(defaultSysTitle, defaultSysTitle);
        checkResponse(OK, GET, REPO_ID, filePath).andExpectAll(jsonPath(SYS_TITLE).value(defaultSysTitle),
                jsonPath(SYS_NAME).value(defaultSysTitle), jsonPath(SYS_PATH).value("/" + defaultSysTitle));
    }

    @Test
    public void docShouldGetRandomlyGeneratedNameWhenNeitherSysNameNorSysTitleDefined() throws Exception {
        String creationFolder = """
                {
                  "sys_description": "Folder with neither sys_name nor sys_title defined",
                  "sys_primaryType": "SysFolder"
                }
                """;
        var result = checkResponseWithPayload(OK, POST, REPO_ID, creationFolder, getRootApiPath());
        String sysName = getJsonPath(result, SYS_NAME);
        result.andExpectAll(jsonPath(SYS_TITLE).value(sysName), jsonPath(SYS_PATH).value("/" + sysName));

        String filePath = getApiPath(sysName, sysName);
        result = checkResponse(OK, GET, REPO_ID, filePath);
        sysName = getJsonPath(result, SYS_NAME);
        result.andExpectAll(jsonPath(SYS_TITLE).value(sysName), jsonPath(SYS_PATH).value("/" + sysName));
    }

    @Test
    public void docReturn400IfPathIsEncoded() throws Exception {
        String encodedDocPath = URLEncoder.encode("/", StandardCharsets.UTF_8);
        String path = "%s/path%s".formatted(PATH, encodedDocPath);
        URI uri = URI.create(path); // we use a URI and not a string to avoid double encoding
        mvc.perform(get(uri).headers(getRepositoryHttpHeaders(REPO_ID)).with(csrf()))
                        .andExpect(status().isBadRequest());
    }
}
