/*
 * (C) Copyright 2025 Hyland (https://hyland.com/) and others.
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
 *     Swarnaprabha Ghosh
 */
package org.nuxeo.rest.bulkimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.nuxeo.rest.bulkimport.BulkImportTestConstants.BUCKET;
import static org.nuxeo.rest.bulkimport.BulkImportTestConstants.CREATOR;
import static org.nuxeo.rest.bulkimport.BulkImportTestConstants.QUEUE;
import static org.nuxeo.rest.bulkimport.BulkImportTestConstants.TEST_DESCRIPTION;
import static org.nuxeo.rest.bulkimport.BulkImportTestConstants.UNIQUE_KEY;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

public class BulkImportMiscellaneousTest {

    private final BulkImport bulkImport = BulkImport.builder()
                                                    .jobId(BulkImportConstants.PROP_JOB_ID)
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
                                                    .awsAccessKeyId(BulkImportConstants.AWS_ACCESS_KEY_ID)
                                                    .awsSecretAccessKey(BulkImportConstants.AWS_SECRET_ACCESS_KEY)
                                                    .awsSessionToken(BulkImportConstants.AWS_SESSION_TOKEN)
                                                    .build();

    @Test
    public void testStatusRecord() {
        List<BulkImport> jobs = List.of(bulkImport);
        BulkImportStatus bulkImportStatusBuild = BulkImportStatus.builder()
                                                                 .jobs(jobs)
                                                                 .count(1)
                                                                 .index(2)
                                                                 .total(3)
                                                                 .build();
        BulkImportStatus bulkImportStatus = BulkImportStatus.builder(bulkImportStatusBuild).build();
        assertNotNull(bulkImportStatus);
        assertEquals(1, bulkImportStatus.count());
        assertEquals(2, bulkImportStatus.index());
        assertEquals(3, bulkImportStatus.total());
    }

    @Test
    public void testReportBulkImportRecord() {
        List<BulkImportFailureDTO> failureDTOS = List.of(BulkImportFailureDTO.builder().build());
        ReportBulkImport reportBulkImport = ReportBulkImport.builder(bulkImport, failureDTOS).build();
        assertNotNull(reportBulkImport);
        assertEquals(BulkImportConstants.PROP_JOB_ID, reportBulkImport.job().jobId());
    }
}
