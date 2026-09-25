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
package com.hyland.openarch.rest.bulkimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hyland.openarch.rest.bulkimport.BulkImportConstants.JOB_STATUS.COMPLETED;
import static com.hyland.openarch.rest.bulkimport.BulkImportTestConstants.ACCESS_KEY_ID;
import static com.hyland.openarch.rest.bulkimport.BulkImportTestConstants.ACCESS_SECRET_KEY;
import static com.hyland.openarch.rest.bulkimport.BulkImportTestConstants.ACCESS_TOKEN;
import static com.hyland.openarch.rest.bulkimport.BulkImportTestConstants.BUCKET;
import static com.hyland.openarch.rest.bulkimport.BulkImportTestConstants.CREATOR;
import static com.hyland.openarch.rest.bulkimport.BulkImportTestConstants.DIGEST;
import static com.hyland.openarch.rest.bulkimport.BulkImportTestConstants.ENCODING;
import static com.hyland.openarch.rest.bulkimport.BulkImportTestConstants.FILE_NAME;
import static com.hyland.openarch.rest.bulkimport.BulkImportTestConstants.IMPORT_KEY;
import static com.hyland.openarch.rest.bulkimport.BulkImportTestConstants.JOB_ID;
import static com.hyland.openarch.rest.bulkimport.BulkImportTestConstants.MIME_TYPE;
import static com.hyland.openarch.rest.bulkimport.BulkImportTestConstants.QUEUE;
import static com.hyland.openarch.rest.bulkimport.BulkImportTestConstants.TEST_DESCRIPTION;
import static com.hyland.openarch.rest.bulkimport.BulkImportTestConstants.UNIQUE_KEY;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.hyland.openarch.rest.document.DocumentDTO;

public class BulkImportMetadataTest {

    private final BulkImport bulkImportDTO = BulkImport.builder()
                                                       .jobId(JOB_ID)
                                                       .name(BulkImportConstants.BULK_IMPORT_TASK_NAME)
                                                       .description(TEST_DESCRIPTION)
                                                       .creator(CREATOR)
                                                       .status(BulkImportConstants.JOB_STATUS.COMPLETED.name())
                                                       .createdAt(Instant.now())
                                                       .startedAt(Instant.now())
                                                       .completedAt(Instant.now())
                                                       .queue(QUEUE)
                                                       .uniqueKey(UNIQUE_KEY)
                                                       .importedDocuments(3)
                                                       .totalDocuments(4)
                                                       .failedDocuments(1)
                                                       .bucket(BUCKET)
                                                       .dryRun(true)
                                                       .awsAccessKeyId(ACCESS_KEY_ID)
                                                       .awsSecretAccessKey(ACCESS_SECRET_KEY)
                                                       .awsSessionToken(ACCESS_TOKEN)
                                                       .build();

    private final DocumentDTO documentDTO = new DocumentDTO();

    private final BulkImportUploadDTO bulkImportUploadDTO = BulkImportUploadDTO.builder()
                                                                               .digest(DIGEST)
                                                                               .encoding(ENCODING)
                                                                               .filename(FILE_NAME)
                                                                               .mimeType(MIME_TYPE)
                                                                               .length(16)
                                                                               .importKey(IMPORT_KEY)
                                                                               .build();

    private final BulkImportMetadata bulkImportMetadata = new BulkImportMetadata();

    @BeforeEach
    public void setUpBulkImportMetaData() {
        bulkImportMetadata.setJobId(JOB_ID);
        bulkImportMetadata.setDocUniqueKey(UNIQUE_KEY);
        bulkImportMetadata.setBulkImport(bulkImportDTO);
        bulkImportMetadata.setBulkImportUpload(bulkImportUploadDTO);
        bulkImportMetadata.setDocument(documentDTO);
    }

    @Test
    void shouldValidateAllMetadataMethods() {
        assertNotNull(bulkImportMetadata.getBulkImport());
        assertNotNull(bulkImportMetadata.getBulkImportUpload());
        assertNotNull(bulkImportMetadata.getDocument());
        assertEquals(JOB_ID, bulkImportMetadata.getJobId());
        assertEquals(UNIQUE_KEY, bulkImportMetadata.getDocUniqueKey());
    }

    @Test
    void shouldValidateBulkImportDTO() {
        assertEquals(JOB_ID, bulkImportDTO.jobId());
        assertEquals(JOB_ID, bulkImportDTO.jobId());
        assertEquals("bulkImport", bulkImportDTO.name());
        assertEquals(TEST_DESCRIPTION, bulkImportDTO.description());
        assertEquals(CREATOR, bulkImportDTO.creator());
        assertEquals(COMPLETED.name(), bulkImportDTO.status());
        assertEquals(QUEUE, bulkImportDTO.queue());
        assertEquals(UNIQUE_KEY, bulkImportDTO.uniqueKey());
        assertEquals(BUCKET, bulkImportDTO.bucket());
        assertEquals(3, bulkImportDTO.importedDocuments());
        assertEquals(4, bulkImportDTO.totalDocuments());
        assertEquals(1, bulkImportDTO.failedDocuments());
        assertTrue(bulkImportDTO.dryRun());
        assertEquals(ACCESS_KEY_ID, bulkImportDTO.awsAccessKeyId());
        assertEquals(ACCESS_SECRET_KEY, bulkImportDTO.awsSecretAccessKey());
        assertEquals(ACCESS_TOKEN, bulkImportDTO.awsSessionToken());
    }

    @Test
    void shouldValidateBulkImportUploadDTO() {
        assertEquals(DIGEST, bulkImportUploadDTO.digest());
        assertEquals(ENCODING, bulkImportUploadDTO.encoding());
        assertEquals(FILE_NAME, bulkImportUploadDTO.filename());
        assertEquals(MIME_TYPE, bulkImportUploadDTO.mimeType());
        assertEquals(IMPORT_KEY, bulkImportUploadDTO.importKey());
        assertEquals(16, bulkImportUploadDTO.length());
    }

}
