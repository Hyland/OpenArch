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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.hyland.openarch.common.xmap.Context;
import com.hyland.openarch.common.xmap.Resource;
import com.hyland.openarch.ecm.core.schema.dto.ContentModelDTO;
import com.hyland.openarch.ecm.core.schema.dto.PrimaryTypeDTO;
import com.hyland.openarch.runtime.RuntimeServiceException;

class TestModelComposer {

    @TempDir
    Path directory;

    private static ContentModelDTO base() {
        return new ContentModelDTO(null, null, null,
                Map.of("SysContent", new PrimaryTypeDTO(null, null, null, null, null)));
    }

    private ModelDescriptor resource(String name, String content, boolean patch) throws Exception {
        URL url = Files.writeString(directory.resolve(name), content).toUri().toURL();
        ModelDescriptor descriptor = new ModelDescriptor();
        descriptor.src = name;
        descriptor.patch = patch;
        descriptor.resource = new Resource(new Context() {
            @Override
            public URL getResource(String ignored) {
                return url;
            }
        }, name);
        return descriptor;
    }

    @Test
    void testFragmentsResolveAcrossResourcesBeforePatches() throws Exception {
        ModelDescriptor primary = resource("primary.json", """
                {"primaryTypes":{"Article":{"mixins":["Authored"]}}}
                """, false);
        ModelDescriptor schema = resource("schema.json", """
                {"schemas":{"article":{"fields":{"art_author":{"type":"author"}}}},
                 "types":{"author":{"extends":"string"}},
                 "mixinTypes":{"Authored":{"schemas":["article"]}}}
                """, false);
        ModelDescriptor patch = resource("review.patch.json", """
                [{"op":"add","path":"/schemas/article/fields/art_reviewed","value":{"type":"boolean"}}]
                """, true);
        SchemaManager manager = ModelComposer.compose(base(), List.of(patch, primary, schema));
        assertTrue(manager.getDocumentType("Article").hasSchema("article"));
        assertEquals("boolean", manager.getField("art_reviewed").getType().getName());
        assertEquals("art", manager.getSchema("article").getPrefix());
    }

    @Test
    void testDuplicateDefinitionsRequirePatch() throws Exception {
        ModelDescriptor first = resource("first.json", """
                {"mixinTypes":{"Marker":{}}}
                """, false);
        ModelDescriptor second = resource("second.json", """
                {"mixinTypes":{"Marker":{}}}
                """, false);
        RuntimeServiceException e = assertThrows(RuntimeServiceException.class,
                () -> ModelComposer.compose(base(), List.of(first, second)));
        assertTrue(e.getMessage().contains("second.json"));
        assertTrue(e.getMessage().contains("/mixinTypes/Marker"));
    }

    @Test
    void testPatchOrderAndReplay() throws Exception {
        ModelDescriptor first = resource("first.patch.json", """
                [{"op":"add","path":"/mixinTypes/Marker","value":{}}]
                """, true);
        ModelDescriptor second = resource("second.patch.json", """
                [{"op":"test","path":"/mixinTypes/Marker","value":{}},
                 {"op":"remove","path":"/mixinTypes/Marker"}]
                """, true);
        assertNull(ModelComposer.compose(base(), List.of(first, second)).getMixin("Marker"));
        assertNotNull(ModelComposer.compose(base(), List.of(first)).getMixin("Marker"));
        assertNull(ModelComposer.compose(base(), List.of()).getMixin("Marker"));
        assertNotNull(ModelComposer.compose(base(), List.of(first)).getMixin("Marker"));
    }

    @Test
    void testInvalidResourcesFailWithSource() throws Exception {
        for (String invalid : List.of("[]", "{\"schemas\":[]}", "{\"scheams\":{}}", "{\"version\":1}", "{")) {
            ModelDescriptor descriptor = resource("invalid.json", invalid, false);
            RuntimeServiceException e = assertThrows(RuntimeServiceException.class,
                    () -> ModelComposer.compose(base(), List.of(descriptor)));
            assertTrue(e.getMessage().contains("invalid.json"));
        }
        ModelDescriptor missing = new ModelDescriptor();
        missing.src = "missing.json";
        assertThrows(RuntimeServiceException.class, () -> ModelComposer.compose(base(), List.of(missing)));
    }

    @Test
    void testInvalidPatchesAndUnresolvedReferencesFail() throws Exception {
        for (String invalid : List.of("{}", """
                [{"op":"remove","path":"/mixinTypes/Missing"}]
                """, """
                [{"op":"replace","path":"","value":[]}]
                """, """
                [{"op":"add","path":"/primaryTypes/Broken","value":{"schemas":["missing"]}}]
                """)) {
            ModelDescriptor descriptor = resource("invalid.patch.json", invalid, true);
            RuntimeServiceException e = assertThrows(RuntimeServiceException.class,
                    () -> ModelComposer.compose(base(), List.of(descriptor)));
            assertTrue(e.getMessage().contains("invalid.patch.json"));
        }
    }
}
