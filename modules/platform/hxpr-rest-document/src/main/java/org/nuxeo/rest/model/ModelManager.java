/*
 * (C) Copyright 2022-2024 Hyland (https://hyland.com/) and others.
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
 *     Adam Dunajewski
 *     Ananya Banerjee
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
import org.nuxeo.ecm.core.schema.validation.SchemaValidationUtils;
import org.nuxeo.ecm.core.schema.validation.violations.ValidationViolation;
import org.nuxeo.rest.model.dto.ModelDTO;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.springboot.transaction.Transactional;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.JsonPatch;

import jakarta.validation.constraints.NotNull;

@Component
public class ModelManager {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Transactional
    public SchemaManager applyPatch(String repoId, String patchBody, boolean validateOnly) throws IOException {
        JsonNode patch = mapper.readTree(patchBody);
        ModelDTO current = ModelDTO.toDTO(getSchemaManagerDTO(repoId));
        JsonNode patched = JsonPatch.apply(patch, mapper.valueToTree(current.toMap()));
        Map<String, Object> patchedDto = mapper.convertValue(patched, Map.class);
        return createSchemaManagerDTO(repoId, patchedDto, validateOnly);
    }

    Map<String, Object> getSchemaManagerDTO(String repoId) {
        return getTypeService().getSchemaManager(repoId).toDTO();
    }

    Map<String, Object> getSchemaManagerDTO(String repoId, @NotNull String projectId) {
        ModelDTO model = ModelDTO.toDTO(getSchemaManagerDTO(repoId));
        model.types().values().removeIf(type -> !projectId.equals(type.projectId()));
        model.schemas().values().removeIf(schema -> !projectId.equals(schema.projectId()));
        model.mixinTypes().values().removeIf(mixinType -> !projectId.equals(mixinType.projectId()));
        model.primaryTypes().values().removeIf(primaryType -> !projectId.equals(primaryType.projectId()));
        return model.toMap();
    }

    void persistSchemaManager(String repoId, SchemaManager schemaManager) {
        getTypeService().setSchemaManager(repoId, schemaManager);
    }

    private TypeService getTypeService() {
        return Framework.getService(TypeService.class);
    }

    /**
     * Returns a consolidated content model for the proposed update specific to a project.
     * <p>
     * Uses the DTO instance prepared from the existing content model and DTO
     * instance created from the request for a supplied projectId .
     * ProjectId supplied as a URL paratamer is set to the
     * corresponding placeholder in the request DTO for every schema/mixintype/primarytype present followed by removing
     * all existing entities associated with the supplied project in the DTO instance and
     * adding the new entities from the request DTO.
     * </p>
     *
     * @param modelDTOFromGet DTO instance prepared using the existing content model
     * @param modelDTOFromRequest DTO instance prepared from the request made for a project
     * @param projectId the one associated with the proposed update
     * @return the consolidated content model for update
     */
    public ModelDTO createConsolidatedContentModel(ModelDTO modelDTOFromGet, ModelDTO modelDTOFromRequest,
            String projectId) {

        Objects.requireNonNull(projectId);

        // Associate the projectId passed in the URL parameter to respective DTO projectId placeholder in request
        modelDTOFromRequest.mixinTypes().entrySet().forEach(item -> item.setValue(item.getValue().withProjectId(projectId)));

        modelDTOFromRequest.primaryTypes().entrySet().forEach(item -> item.setValue(item.getValue().withProjectId(projectId)));

        modelDTOFromRequest.schemas().entrySet().forEach(item -> item.setValue(item.getValue().withProjectId(projectId)));

        modelDTOFromRequest.types().entrySet().forEach(item -> item.setValue(item.getValue().withProjectId(projectId)));

        var mixinTypeDTOMap = modelDTOFromGet.mixinTypes();
        mixinTypeDTOMap.values().removeIf(mixinType -> projectId.equals(mixinType.projectId()));
        mixinTypeDTOMap.putAll(modelDTOFromRequest.mixinTypes());

        var schemaDTOMap = modelDTOFromGet.schemas();
        schemaDTOMap.values().removeIf(schema -> projectId.equals(schema.projectId()));
        schemaDTOMap.putAll(modelDTOFromRequest.schemas());

        var primaryTypeDTOMap = modelDTOFromGet.primaryTypes();
        primaryTypeDTOMap.values().removeIf(primaryType -> projectId.equals(primaryType.projectId()));
        primaryTypeDTOMap.putAll(modelDTOFromRequest.primaryTypes());

        var typeDTOMap = modelDTOFromGet.types();
        typeDTOMap.values().removeIf(type -> projectId.equals(type.projectId()));
        typeDTOMap.putAll(modelDTOFromRequest.types());

        return modelDTOFromGet;
    }

    public SchemaManager createSchemaManagerDTO(String repoId, Map<String, Object> dto, boolean validateOnly) {
        List<ValidationViolation> violations = new ArrayList<>();
        Map<String, Object> current = this.getSchemaManagerDTO(repoId);
        // normalize current into map
        Map<String, Object> map = ModelDTO.toDTO(current).toMap();
        SchemaValidationUtils.validateDTODiff(map, dto, violations);
        return new SchemaManagerDTO(dto, violations, validateOnly);
    }
}
