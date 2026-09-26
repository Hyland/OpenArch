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

import static com.hyland.openarch.rest.document.test.AbstractDocumentAPITest.COMPLEX_UPLOAD_ID;
import static com.hyland.openarch.rest.document.test.AbstractDocumentAPITest.EXTERNAL_URL_UPLOAD_ID;
import static com.hyland.openarch.rest.document.test.AbstractDocumentAPITest.MIMETYPE_TEXT_PLAIN;
import static com.hyland.openarch.rest.document.test.AbstractDocumentAPITest.MIMETYPE_TEXT_URI_LIST;

import java.nio.charset.StandardCharsets;

import org.springframework.mock.web.MockMultipartFile;

public class MultiPartTestHelper {

    private MultiPartTestHelper() {
        // Utility class
    }

    public static MockMultipartFile getMainUploadIdPart() {
        return new MockMultipartFile("mainUploadId", "test.txt", MIMETYPE_TEXT_PLAIN,
                "test content".getBytes(StandardCharsets.UTF_8));
    }

    public static MockMultipartFile getComplexUploadIdPart() {
        return new MockMultipartFile(COMPLEX_UPLOAD_ID, "test2.txt", MIMETYPE_TEXT_PLAIN,
                "test content 2".getBytes(StandardCharsets.UTF_8));
    }

    public static MockMultipartFile getListUploadIdPart() {
        return new MockMultipartFile("listUploadId", "test3.txt", MIMETYPE_TEXT_PLAIN,
                "test content 3".getBytes(StandardCharsets.UTF_8));
    }

    public static MockMultipartFile getExternalUrlPart(String externalUrl) {
        return new MockMultipartFile(EXTERNAL_URL_UPLOAD_ID, null, MIMETYPE_TEXT_URI_LIST,
                externalUrl.getBytes(StandardCharsets.UTF_8));
    }

    public static String simpleUploadWithExternalUrlJson(String uploadId1, String uploadId2) {
        return String.format("""
            {
                "sys_title": "My File",
                "sysfile_blob":
                {
                    "uploadId": "%s"
                },
                "sysfiles_blobs":[
                         {"blob":{"uploadId":"%s"}}
                     ],
                "sys_primaryType": "SysFile"
            }
            """, uploadId1, uploadId2);
    }

    public static String simpleUploadWithoutExternalUrlJson(String uploadId) {
        return String.format("""
            {
                "sys_title": "My File",
                "sysfile_blob":
                {
                    "uploadId": "%s"
                },
                "sys_primaryType": "SysFile"
            }
            """, uploadId);
    }

    public static String simpleUploadWithSysFilesBlobsJson(String uploadId) {
        return String.format("""
            {
                "sys_title": "My File",
                "sysfiles_blobs":[
                         {"blob":{"uploadId":"%s"}}
                     ],
                "sys_primaryType": "SysFile"
            }
            """, uploadId);
    }

    public static String getBlobCreationJson(String mainId, String complexId, String listId) {
        return """
                {
                  "sys_title": "My File",
                  "sysfile_blob": {
                    "uploadId": "%s"
                  },
                  "tp_fileComplexList": [
                    {
                      "blobItem": {
                        "uploadId": "%s"
                      },
                      "filenameItem": "bar"
                    }
                  ],
                  "tp_fileList": [
                    {
                      "uploadId": "%s"
                    }
                  ],
                  "sys_primaryType": "TestDocument"
                }
                """.formatted(mainId, complexId, listId);
    }

    public static String getBlobCreationJsonWithOneBinary(String mainId) {
        return """
                {
                  "sys_title": "My File",
                  "sysfile_blob": {
                    "uploadId": "%s"
                  },
                  "sys_primaryType": "TestDocument"
                }
                """.formatted(mainId);
    }

    public static String getBlobCreationJsonWithTwoBinary(String mainId, String complexId) {
        return """
                {
                  "sys_title": "My File",
                  "sysfile_blob": {
                    "uploadId": "%s"
                  },
                  "tp_fileComplexList": [
                    {
                      "blobItem": {
                        "uploadId": "%s"
                      },
                      "filenameItem": "bar"
                    }
                  ],
                  "sys_primaryType": "TestDocument"
                }
                """.formatted(mainId, complexId);
    }

    public static String getBlobCreationJsonWithThreeBinary(String mainId, String complexId, String listId) {
        return """
                {
                  "sys_title": "My File",
                  "sysfile_blob": {
                    "uploadId": "%s"
                  },
                  "tp_fileComplexList": [
                    {
                      "blobItem": {
                        "uploadId": "%s"
                      },
                      "filenameItem": "bar"
                    }
                  ],
                  "tp_fileList": [
                    {
                      "uploadId": "%s"
                    }
                  ],
                  "sys_primaryType": "TestDocument"
                }
                """.formatted(mainId, complexId, listId);
    }

    public static String getInvalidBlobCreationJson(String uploadId) {
        // references a blob field that does not exist on Folder document
        return """
                {
                  "sys_title": "My File",
                  "sysfile_blob": {
                    "uploadId": "%s"
                  },
                  "sys_primaryType": "SysFolder"
                }
                """.formatted(uploadId);
    }

}
