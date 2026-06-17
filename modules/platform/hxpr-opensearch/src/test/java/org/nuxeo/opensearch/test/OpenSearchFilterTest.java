/*
 * (C) Copyright 2021-2024 Nuxeo (http://nuxeo.com/) and others.
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
 *     Nelson Silva
 */
package org.nuxeo.opensearch.test;

import static org.hamcrest.CoreMatchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nuxeo.opensearch.OpenSearchFilter;
import org.nuxeo.opensearch.listener.OpenSearchInlineListener;
import org.nuxeo.runtime.springboot.junit.RuntimeSpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

/**
 * Unit tests for {@link OpenSearchFilter}.
 */
@SpringBootTest(classes = { OpenSearchFilterTest.ExceptionTestResource.class, OpenSearchFilter.class })
class OpenSearchFilterTest extends RuntimeSpringBootTest {

    @RestController
    @RequestMapping("/es")
    public static class ExceptionTestResource {
        @GetMapping("/sync")
        public Boolean sync() {
            return OpenSearchInlineListener.useSyncIndexing.get();
        }

    }

    @Test
    @WithMockUser
    void testESSyncHeader() throws Exception {
        mvc.perform(get("/es/sync")).andExpectAll(status().isOk(), content().string(is("false")));

        mvc.perform(get("/es/sync").header(OpenSearchFilter.OPENSEARCH_SYNC_FLAG, "true"))
           .andExpectAll(status().isOk(), content().string(is("true")));
    }

    @BeforeEach
    public void setup(@Autowired WebApplicationContext context, @Autowired OpenSearchFilter openSearchFilter) {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                             .apply(SecurityMockMvcConfigurers.springSecurity())
                             .addFilters(openSearchFilter)
                             .build();
    }
}
