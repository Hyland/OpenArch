/*
 * (C) Copyright 2023-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.user;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import com.hyland.openarch.runtime.springboot.junit.RuntimeSpringBootTest;

/**
 * Abstract base class for {@link GroupAPI} tests, shared between community (mock) and enterprise (real IAM) variants.
 * Subclasses provide the Spring/Nuxeo annotations and the {@link UserManager} implementation via their feature/config.
 */
public abstract class AbstractGroupAPITest extends RuntimeSpringBootTest {

    static final String TEST_GROUP_ID = "e41b8531-41ec-42c2-bd4d-91e5bcbf5e49";

    static final String TEST_GROUP_NAME = "myGroupName";

    static final String TEST_GROUP_ID_TWO = "983c5bf9-5c98-4118-a289-af562fa36d5a";

    static final String TEST_GROUP_NAME_TWO = "myGroupNameTwo";

    /**
     * @return the minimum number of groups expected to be returned when requesting all groups (e.g. with an empty name)
     * set default to MAX_VALUE, so a proper value is must set to each testcase otherwise 0 lets always pass the test
     */
    protected int minGroupSize() {
        return Integer.MAX_VALUE;
    }

    @Test
    void shouldFindGroupById() throws Exception {
        mvc.perform(get(GroupAPI.PATH + "/" + TEST_GROUP_ID)
            .header("Content-Type", "application/json")
            .accept("application/json"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(TEST_GROUP_ID)))
            .andExpect(jsonPath("$.name", is(TEST_GROUP_NAME)));
    }

    @Test
    void shouldNotFindGroupByIdWhenNoExactMatch() throws Exception {
        mvc.perform(get(GroupAPI.PATH + "/name")
            .header("Content-Type", "application/json")
            .accept("application/json"))
            .andExpect(status().is(404));
    }

    @Test
    void shouldFindGroupsByNamePart() throws Exception {
        mvc.perform(get(GroupAPI.PATH + "?name=" + TEST_GROUP_NAME))
            .andExpect(jsonPath("$.size()", is(2)))
            .andExpect(jsonPath("$[*].id", containsInAnyOrder(TEST_GROUP_ID, TEST_GROUP_ID_TWO)))
            .andExpect(jsonPath("$[*].name", containsInAnyOrder(TEST_GROUP_NAME, TEST_GROUP_NAME_TWO)));
    }

    @Test
    void shouldFindNoGroupsByNamePartWhenNoMatch() throws Exception {
        mvc.perform(get(GroupAPI.PATH + "?name=other"))
            .andExpect(jsonPath("$.size()", is(0)));
    }

    @Test
    void shouldReturnAllGroupsWhenNameEmpty() throws Exception {
        mvc.perform(get(GroupAPI.PATH + "?name="))
               .andExpect(jsonPath("$.size()").value(Matchers.greaterThanOrEqualTo((minGroupSize()))));
    }
}
