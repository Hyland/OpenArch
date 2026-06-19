/*
 * (C) Copyright 2024 Hyland (https://www.hyland.com/) and others.
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
 *     Saumit Mandal
 */
package org.nuxeo.rest.query.test;

import static jakarta.ws.rs.core.Response.Status.OK;
import static org.nuxeo.rest.query.QueryAPI.PATH;
import static org.nuxeo.rest.query.io.DocumentQueryDTO.LIMIT_PROP;
import static org.nuxeo.rest.query.io.DocumentQueryDTO.OFFSET_PROP;
import static org.nuxeo.rest.query.io.DocumentQueryDTO.QUERY_STRING_PROP;
import static org.nuxeo.rest.utils.Headers.HXCS_REPOSITORY;
import static org.springframework.http.HttpMethod.POST;

import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nuxeo.rest.query.QueryAPI;
import org.nuxeo.rest.utils.Versions;
import org.nuxeo.runtime.test.runner.Features;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;

@Import({ QueryAPITestConfiguration.class })
@SpringBootTest(classes = { QueryAPI.class })
@Features(WildcardQueryFeature.class)
@Tag("useOpenSearch")
public class WildcardFullTextQueryAPITest extends AbstractQueryAPITest {

    // check query api rest endpoint
    @Test
    public void testQueryAllWithSpecificParametersPOSTV1() throws Exception {
        String query = "SELECT * FROM SysContent WHERE sys_fulltext = 'assignments*'";
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, Versions.APP_HXCS_JSON_V1);
        headers.set(HXCS_REPOSITORY, REPO_ID);

        var r = checkResponse(OK, PATH, POST, headers, Map.of(QUERY_STRING_PROP, query,
                LIMIT_PROP, -1,
                OFFSET_PROP, 0));
        checkQueryResponseBody("rest/query_res_wildcard.json", r, " WHERE sys_fulltext = 'assignments*'");
    }

}
