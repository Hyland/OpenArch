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
package org.nuxeo.rest.user;

import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON;
import static org.nuxeo.rest.utils.Versions.APP_HXCS_JSON_V1;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.rest.swagger.annotation.ApiResponse200;
import org.nuxeo.rest.swagger.annotation.ApiResponse400;
import org.nuxeo.rest.swagger.annotation.ApiResponse404;
import org.nuxeo.rest.swagger.annotation.Authenticated;
import com.hyland.openarch.runtime.api.Framework;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * User Rest API.
 */
@RestController
@RequestMapping(path = UserAPI.PATH, produces = { APPLICATION_JSON_VALUE, APP_HXCS_JSON, APP_HXCS_JSON_V1 })
@Tag(name = "User", description = "User API (read-only)")
@Authenticated
public class UserAPI {

    public static final String PATH = "/api/user"; // NOSONAR

    protected UserManager getUserManager() {
        return Framework.getService(UserManager.class);
    }

    protected RepositoryPrincipal getUser(String id) {
        return getUserManager().getPrincipal(id);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Returns the user with given id.", operationId = "getUserById")
    @ApiResponse200(description = "Returns the user with given id.")
    @ApiResponse404(description = "User not found.")
    public UserDTO getUserById(@PathVariable("userId") @Parameter(description = "The user id.") String userId) {
        RepositoryPrincipal principal = getUser(userId);
        if (principal == null) {
            throw new RepositoryException(NOT_FOUND.value());
        }
        return new UserDTO(principal);
    }

    @GetMapping
    @Operation(summary = "Returns the list of users which username or email address contains the given string", operationId = "searchUsersByName")
    @ApiResponse200(description = "Returns the list of users which username or email address contains the given string")
    @ApiResponse400(description = "Given name part for searching is null or empty.")
    public List<UserDTO> searchUsersByName(@RequestParam("name") @Parameter(description = "Part of the user name or email", required = true) String searchName) {
        if (null == searchName || searchName.isBlank()) {
            throw new RepositoryException("Given name part for searching is null or empty", BAD_REQUEST.value());
        }
        return getUserManager().searchPrincipalsByName(searchName).stream().map(UserDTO::new).toList();
    }
}
