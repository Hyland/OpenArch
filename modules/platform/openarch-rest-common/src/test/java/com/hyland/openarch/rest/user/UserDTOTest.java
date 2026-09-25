/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link UserDTO}.
 */
class UserDTOTest {

    @Test
    void should_set_properties_to_empty_map_when_null_is_passed_via_canonical_constructor() {
        UserDTO userDTO = new UserDTO("user-1", null, null, null, null, null);

        assertNotNull(userDTO.properties());
        assertEquals(Map.of(), userDTO.properties());
    }

    @Test
    void should_keep_properties_when_non_null_map_is_passed_via_canonical_constructor() {
        Map<String, Serializable> props = new HashMap<>();
        props.put("department", "Engineering");

        UserDTO userDTO = new UserDTO("user-1", null, null, null, null, props);

        assertNotNull(userDTO.properties());
        assertEquals(props, userDTO.properties());
    }

    @Test
    void should_set_properties_to_empty_map_when_created_via_id_only_constructor() {
        UserDTO userDTO = new UserDTO("user-1");

        assertNotNull(userDTO.properties());
        assertEquals(Map.of(), userDTO.properties());
    }

    @Test
    void should_throw_NullPointerException_when_id_is_null() {
        assertThrows(NullPointerException.class, () -> new UserDTO(null, null, null, null, null, null));
    }
}
