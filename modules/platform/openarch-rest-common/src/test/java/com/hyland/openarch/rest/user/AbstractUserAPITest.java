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
package com.hyland.openarch.rest.user;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsIn.oneOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import com.hyland.openarch.runtime.springboot.junit.RuntimeSpringBootTest;

/**
 * Abstract base class for {@link UserAPI} tests, shared between community (mock) and enterprise (real IAM) variants.
 * Subclasses provide the Spring/OpenArch annotations and the {@link UserManager} implementation via their feature/config.
 */
public abstract class AbstractUserAPITest extends RuntimeSpringBootTest {

    static final String TEST_USER_ID = "ff7b0360-3594-416c-b587-2fa4dd0839ce";

    static final String TEST_USER_2_ID = "850edbd1-33c3-4653-b7c9-15e594570f28";

    static final String TEST_USER_3_ID = "850edbd1-33c3-4653-b7c9-15e594570f29";

    @Test
    void testExistentUser() throws Exception {
        mvc.perform(get(UserAPI.PATH + "/" + TEST_USER_ID))
               .andExpect(jsonPath("$.id", is(TEST_USER_ID)));
    }

    @Test
    void testNonexistentUser() throws Exception {
        mvc.perform(get(UserAPI.PATH + "/notfound"))
               .andExpect(status().is(404));
    }

    @Test
    void shouldFindUserByUserNamePart() throws Exception {
        mvc.perform(get(UserAPI.PATH + "?name=myUserNameTwo"))
               .andExpect(jsonPath("$.size()", is(1)))
               .andExpect(jsonPath("$[0].id", is(TEST_USER_3_ID)))
               .andExpect(jsonPath("$[0].email", is("firstTwo.lastnameTwo@hyland.com")))
               .andExpect(jsonPath("$[0].firstName", is("MyFirstTwo")))
               .andExpect(jsonPath("$[0].lastName", is("MyFamilyTwo")))
               .andExpect(jsonPath("$[0].username", is("myUserNameTwo")));
    }

    @Test
    void shouldFindUsersByUserNamePart() throws Exception {
        mvc.perform(get(UserAPI.PATH + "?name=UserName"))
               .andExpect(jsonPath("$[0].id", is(oneOf(TEST_USER_2_ID, TEST_USER_3_ID))));
        mvc.perform(get(UserAPI.PATH + "?name=UserN"))
               .andExpect(jsonPath("$[1].id", is(oneOf(TEST_USER_2_ID, TEST_USER_3_ID))));
    }

    @Test
    void shouldFindNoUsersByNamePartWhenNoMatch() throws Exception {
        mvc.perform(get(UserAPI.PATH + "?name=Kas"))
               .andExpect(jsonPath("$.size()", is(0)));
    }

    @Test
    void shouldFindUserWithSameFirstAndLastNameOnlyOnce() throws Exception {
        mvc.perform(get(UserAPI.PATH + "?name=tiry"))
               .andExpect(jsonPath("$.size()", is(1)));
    }

    @Test
    void shouldReturnBadRequestWhenNameEmpty() throws Exception {
        mvc.perform(get(UserAPI.PATH + "?name="))
               .andExpect(status().is(400));
    }
}
