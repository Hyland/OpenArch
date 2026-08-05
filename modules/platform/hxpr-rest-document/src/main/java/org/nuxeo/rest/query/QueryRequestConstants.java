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
package org.nuxeo.rest.query;

public class QueryRequestConstants {

    private QueryRequestConstants() {
        // Not to be instantiated
    }

    public static final String TERMS_AGGREGATION_INVALID_INPUT_EXAMPLE = """
            {
              "entity-type": "exception",
              "message": "Invalid input",
              "status": 400
            }
            """;

    public static final String TERMS_AGGREGATION_RESPONSE_EXAMPLE = """
            {
              "aggregationsBuckets": [
                {"key": "key-1", "docCount": 79},
                {"key": "key-2", "docCount": 63},
                {"key": "key-3", "docCount": 54}
              ],
              "documents": [],
              "count": 0,
              "limit": 0,
              "offset": 0,
              "query": "SELECT * FROM SysContent",
              "repositoryId": "default",
              "totalCount": 0,
              "totalCountIsTruncated": true,
              "trackTotalCount": true
            }
            """;
}
