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
package com.hyland.openarch.rest.user;

import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static com.hyland.openarch.rest.utils.Versions.APP_HXCS_JSON;
import static com.hyland.openarch.rest.utils.Versions.APP_HXCS_JSON_V1;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

import com.hyland.openarch.ecm.core.api.RepositoryGroup;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import com.hyland.openarch.rest.swagger.annotation.ApiResponse200;
import com.hyland.openarch.rest.swagger.annotation.ApiResponse404;
import com.hyland.openarch.rest.swagger.annotation.Authenticated;
import com.hyland.openarch.runtime.api.Framework;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = GroupAPI.PATH, produces = { APPLICATION_JSON_VALUE, APP_HXCS_JSON, APP_HXCS_JSON_V1 })
@Tag(name = "Group", description = "GroupAPI (read-only)")
@Authenticated
public class GroupAPI {

    public static final String PATH = "/api/group"; // NOSONAR

    protected UserManager getUserManager() {
        return Framework.getService(UserManager.class);
    }

    @GetMapping("/{groupId}")
    @Operation(summary = "Returns the group with given id.", operationId = "getGroupById")
    @ApiResponse200(description = "Returns the group with given id.")
    @ApiResponse404(description = "Group not found.")
    public GroupDTO getGroupById(@PathVariable("groupId") @Parameter(description = "The group id.") String groupId) {
        RepositoryGroup foundGroup = getUserManager().getGroup(groupId);
        if (foundGroup == null) {
            throw new RepositoryException(NOT_FOUND.getStatusCode());
        }
        return new GroupDTO(foundGroup);
    }

    @GetMapping
    @Operation(summary = "Returns the list of groups which name or label contains the given string. Given name part for searching is null or empty, then return all groups", operationId = "searchGroups")
    @ApiResponse200(description = "Returns the list of groups which name or label contains the given string.")
    public List<GroupDTO> searchGroups(
            @RequestParam("name") @Parameter(description = "Part of the group name or label") String namePart) {
        return getUserManager().searchGroupsByName(namePart).stream().map(GroupDTO::new).toList();
    }
}
