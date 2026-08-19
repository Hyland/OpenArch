/*
 * (C) Copyright 2022-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.rest.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.SchemaManagerDTO;
import org.nuxeo.ecm.core.schema.TypeService;
import org.nuxeo.ecm.core.schema.dto.ContentModelDTO;
import org.nuxeo.ecm.core.schema.dto.ModelMapCodec;
import org.nuxeo.ecm.core.schema.validation.SchemaValidationUtils;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;
import org.nuxeo.runtime.api.Framework;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.Jackson3JsonPatch;

import jakarta.validation.constraints.NotNull;

@Component
public class ModelManager {

    private static final ObjectMapper mapper = new ObjectMapper();

    public SchemaManager applyPatch(String repoId, String patchBody, boolean validateOnly) throws IOException {
        JsonNode patch = mapper.readTree(patchBody);
        // the patch applies to the model as the API renders it, i.e. to what GET returns
        JsonNode patched = Jackson3JsonPatch.apply(patch, mapper.valueToTree(getContentModel(repoId)));
        // read the patch result ourselves rather than letting Jackson bind it, so that a value of
        // the wrong shape is reported as a validation violation instead of a binding failure
        List<ValidationViolation> violations = new ArrayList<>();
        Map<String, Object> patchedMap = mapper.convertValue(patched, Map.class);
        ContentModelDTO model = ModelMapCodec.read(patchedMap, violations);
        // diff against the patch result as it stands, not against the model read out of it: an
        // entry the reader had to drop still counts as a modification, not as a removal
        return createSchemaManager(repoId, model, patchedMap, violations, validateOnly);
    }

    ContentModelDTO getContentModel(String repoId) {
        return getTypeService().getSchemaManager(repoId).toContentModel();
    }

    ContentModelDTO getContentModel(String repoId, @NotNull String projectId) {
        return getContentModel(repoId).filterByProject(projectId);
    }

    void persistSchemaManager(String repoId, SchemaManager schemaManager) {
        getTypeService().setSchemaManager(repoId, schemaManager);
    }

    private TypeService getTypeService() {
        return Framework.getService(TypeService.class);
    }

    /**
     * Returns a consolidated content model for the proposed update specific to a project: every
     * item currently belonging to {@code projectId} is dropped, and the contributed ones are added
     * in its place, stamped with that project.
     *
     * @param current the existing content model
     * @param contributed the content model from the request made for a project
     * @param projectId the one associated with the proposed update
     * @return the consolidated content model for update
     */
    public ContentModelDTO createConsolidatedContentModel(ContentModelDTO current, ContentModelDTO contributed,
            String projectId) {
        Objects.requireNonNull(projectId);
        return current.withProjectItems(projectId, contributed);
    }

    public SchemaManager createSchemaManager(String repoId, ContentModelDTO model, boolean validateOnly) {
        return createSchemaManager(repoId, model, ModelMapCodec.write(model), new ArrayList<>(), validateOnly);
    }

    /**
     * @param model the requested model
     * @param requestedMap the request as it stands, which is what the diff reports values from
     */
    private SchemaManager createSchemaManager(String repoId, ContentModelDTO model,
            Map<String, Object> requestedMap, List<ValidationViolation> violations, boolean validateOnly) {
        SchemaValidationUtils.validateDTODiff(ModelMapCodec.write(getContentModel(repoId)), requestedMap, violations);
        return new SchemaManagerDTO(model, violations, validateOnly);
    }
}
