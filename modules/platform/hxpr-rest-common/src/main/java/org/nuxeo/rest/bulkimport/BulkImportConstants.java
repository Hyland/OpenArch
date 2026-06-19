/*
 * (C) Copyright 2024 Hyland (https://hyland.com/) and others.
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
 *     Ananya Banerjee
 */
package org.nuxeo.rest.bulkimport;

import java.time.Duration;

public class BulkImportConstants {

    public static final String AWS_ACCESS_KEY_ID = "awsAccessKeyId";

    public static final String AWS_SECRET_ACCESS_KEY = "awsSecretAccessKey";

    public static final String AWS_SESSION_TOKEN = "awsSessionToken";

    public static final int CHUNK_SIZE = 1000;

    public static final String DEFAULT_REPOSITORY_ID = "default";

    public static final String BULK_IMPORT_API_PATH = "/api/import";

    public static final String DRY_RUN_ONLY = "dryRun";

    public static final String FORMAT = "format";

    public static final String REPORT_FORMAT_VALUE = "json";

    public static final String REPORT_FORMAT_EXTENSION = ".json";

    public static final String HXPR_BULK_INGESTION_S_3_UPLOAD_ROLE_ARN = "hxpr.bulk.ingestion.s3.upload.roleArn";

    public static final String HXPR_BULK_INGESTION_S3_UPLOAD_BUCKET = "hxpr.bulk.ingestion.s3.upload.bucket";

    public enum JOB_STATUS {
        CREATED, VALID, INVALID, QUEUED, RUNNING, COMPLETED, FAILED, PAUSED, CANCELLED
    }

    public static final String PROP_DOC_UNIQUE_KEY = "the_unique_key";

    public static final String PROP_JOB_ID = "jobId";

    public static final String PROP_IMPORT_KEY = "import_key";

    public static final String BULK_IMPORT_TASK_NAME = "bulkImport";

    public static final String METADATA_FILTER = "bulk-import-metadata.json";

    public static final String PATH_SEPARATOR = "/";

    public static final String UNDERSCORED = "_";

    public static final Duration PRE_SIGN_EXPIRY = Duration.ofMinutes(10);

    public static final int BATCH_SIZE = 1;

    public static final String FAILED_FOLDER = "failed";

    public static final String FAILED_FILE_PREFIX = "task_";

    public static final String JOB_PREFIX = "job_";

    public static final String SUCCESS_PREFIX = "success_";

    public static final String FAILURE_PREFIX = "failure_";

    public static final String TOTAL_PREFIX = "total_";

    public static final String STORE_NAME = "bulkIngestStore";

    public static final String PREFIX_JOB_ABORT_STATUS = "abortStatus_";

    public static final String ABORTED = "ABORTED";

    public static final String COMPLETED_TASKS_PREFIX = "completed_tasks_";

    public static final String TOTAL_TASKS_PREFIX = "total_tasks_";
}
