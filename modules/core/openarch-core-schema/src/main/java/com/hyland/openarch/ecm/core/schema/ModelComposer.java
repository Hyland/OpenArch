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
package com.hyland.openarch.ecm.core.schema;

import static com.hyland.openarch.ecm.core.schema.SchemaManagerConstants.MIXIN_TYPES;
import static com.hyland.openarch.ecm.core.schema.SchemaManagerConstants.PRIMARY_TYPES;
import static com.hyland.openarch.ecm.core.schema.SchemaManagerConstants.SCHEMAS;
import static com.hyland.openarch.ecm.core.schema.SchemaManagerConstants.TYPES;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.flipkart.zjsonpatch.CompatibilityFlags;
import com.flipkart.zjsonpatch.Jackson3JsonPatch;
import com.flipkart.zjsonpatch.JsonPatchApplicationException;
import com.hyland.openarch.ecm.core.schema.dto.ContentModelDTO;
import com.hyland.openarch.ecm.core.schema.dto.ModelMapCodec;
import com.hyland.openarch.ecm.core.schema.dto.PrimaryTypeDTO;
import com.hyland.openarch.ecm.core.schema.validation.SchemaValidationException;
import com.hyland.openarch.ecm.core.schema.validation.violations.ValidationViolation;
import com.hyland.openarch.runtime.RuntimeServiceException;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Composes definitions before resolving references, then replays patches in contribution order. */
final class ModelComposer {

    private static final ObjectMapper MAPPER = JsonMapper.builder(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).build();

    private static final Set<String> SECTIONS = Set.of(SCHEMAS, TYPES, MIXIN_TYPES, PRIMARY_TYPES);

    private ModelComposer() {
    }

    static SchemaManagerDTO compose(ContentModelDTO base, List<ModelDescriptor> contributions) {
        ObjectNode model = MAPPER.valueToTree(base);
        for (ModelDescriptor contribution : contributions) {
            if (!contribution.patch) {
                merge(model, read(contribution), contribution.src);
            }
        }
        ObjectNode primaryTypes = (ObjectNode) model.get(PRIMARY_TYPES);
        if (!primaryTypes.has(TypeConstants.SYS_CONTENT)) {
            primaryTypes.set(TypeConstants.SYS_CONTENT,
                    MAPPER.valueToTree(new PrimaryTypeDTO(null, null, null, null, null)));
        }
        JsonNode patched = model;
        for (ModelDescriptor contribution : contributions) {
            if (contribution.patch) {
                JsonNode patch = read(contribution);
                if (!patch.isArray()) {
                    throw invalid(contribution.src, "a JSON Patch must be an array");
                }
                try {
                    patched = Jackson3JsonPatch.apply(patch, patched,
                            EnumSet.of(CompatibilityFlags.FORBID_REMOVE_MISSING_OBJECT));
                } catch (JsonPatchApplicationException | IllegalArgumentException e) {
                    throw new RuntimeServiceException("Cannot apply content-model patch " + contribution.src, e);
                }
            }
        }
        if (!patched.isObject()) {
            throw invalid(sources(contributions), "the composed model must be an object");
        }
        checkSections(patched, sources(contributions));
        List<ValidationViolation> violations = new ArrayList<>();
        Map<String, Object> map = MAPPER.convertValue(patched, Map.class);
        ContentModelDTO dto = ModelMapCodec.read(map, violations);
        if (!dto.primaryTypes().containsKey(TypeConstants.SYS_CONTENT)) {
            throw invalid(sources(contributions), "the composed model must retain SysContent");
        }
        try {
            return new SchemaManagerDTO(dto, violations, false, true);
        } catch (SchemaValidationException e) {
            throw new RuntimeServiceException("Invalid composed content model from " + sources(contributions), e);
        }
    }

    private static JsonNode read(ModelDescriptor contribution) {
        URL url = contribution.resource == null ? null : contribution.resource.toURL();
        if (url == null) {
            throw invalid(contribution.src, "resource not found");
        }
        try (InputStream stream = url.openStream()) {
            return MAPPER.readTree(stream);
        } catch (IOException | JacksonException e) {
            throw new RuntimeServiceException("Cannot read content-model resource " + contribution.src, e);
        }
    }

    private static void merge(ObjectNode model, JsonNode fragment, String source) {
        if (!fragment.isObject()) {
            throw invalid(source, "a model fragment must be an object");
        }
        checkSections(fragment, source);
        fragment.properties().forEach(section -> {
            if (!section.getValue().isObject()) {
                throw invalid(source, section.getKey() + " must be an object");
            }
            ObjectNode target = (ObjectNode) model.get(section.getKey());
            section.getValue().properties().forEach(entry -> {
                if (target.has(entry.getKey())) {
                    throw invalid(source, "duplicate definition at /" + section.getKey() + "/" + entry.getKey()
                            + "; use a patch to change an existing definition");
                }
                target.set(entry.getKey(), entry.getValue());
            });
        });
    }

    private static void checkSections(JsonNode model, String source) {
        model.propertyNames().forEach(name -> {
            if (!SECTIONS.contains(name)) {
                throw invalid(source, "unknown model section " + name);
            }
        });
    }

    private static String sources(List<ModelDescriptor> contributions) {
        return contributions.stream().map(c -> c.src).toList().toString();
    }

    private static RuntimeServiceException invalid(String source, String message) {
        return new RuntimeServiceException("Invalid content-model resource " + source + ": " + message);
    }
}
