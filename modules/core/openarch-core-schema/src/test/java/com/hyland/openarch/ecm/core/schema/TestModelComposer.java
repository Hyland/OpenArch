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
import com.hyland.openarch.common.xmap.XMap;
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
        descriptor.resource = new Resource(context(url), name);
        return descriptor;
    }

    private static Context context(URL url) {
        return new Context() {
            @Override
            public URL getResource(String ignored) {
                return url;
            }
        };
    }

    @Test
    void testMinimalBootstrapAndExplicitRoot() throws Exception {
        RuntimeServiceException e = assertThrows(RuntimeServiceException.class,
                () -> ModelComposer.compose(ContentModelDTO.empty(), List.of()));
        assertTrue(e.getMessage().contains("the composed model must retain SysContent"));
        ModelDescriptor root = resource("root.json", """
                {"mixinTypes":{"RootMarker":{}},
                 "primaryTypes":{"SysContent":{"mixins":["RootMarker"]},"Child":{}}}
                """, false);
        SchemaManager manager = ModelComposer.compose(ContentModelDTO.empty(), List.of(root));
        assertTrue(manager.getDocumentType("SysContent").hasMixin("RootMarker"));
        assertEquals("SysContent", manager.getDocumentType("Child").getSuperType().getName());
    }

    @Test
    void testSysContentSchemasAndRemovalAreExplicit() throws Exception {
        ModelDescriptor root = resource("root.json", """
                {"schemas":{"system":{"fields":{"sys_value":{"type":"string"}}}},
                 "primaryTypes":{"SysContent":{}}}
                """, false);
        SchemaManager manager = ModelComposer.compose(ContentModelDTO.empty(), List.of(root));
        assertNull(manager.getDocumentType("SysContent").getSuperType());
        assertTrue(manager.getDocumentType("SysContent").getSchemaNames().isEmpty());
        ModelDescriptor remove = resource("remove.patch.json", """
                [{"op":"remove","path":"/primaryTypes/SysContent"}]
                """, true);
        assertThrows(RuntimeServiceException.class,
                () -> ModelComposer.compose(ContentModelDTO.empty(), List.of(root, remove)));
    }

    @Test
    void testSysContentCannotDeclareAParent() throws Exception {
        ModelDescriptor root = resource("root.json", """
                {"primaryTypes":{"SysContent":{"extends":"Other"},"Other":{}}}
                """, false);
        RuntimeServiceException e = assertThrows(RuntimeServiceException.class,
                () -> ModelComposer.compose(ContentModelDTO.empty(), List.of(root)));
        assertTrue(e.getCause().getMessage().contains("primaryTypes/SysContent/extends"));
    }

    @Test
    void testImplicitParentMustBeContributed() throws Exception {
        ModelDescriptor child = resource("child.json", """
                {"primaryTypes":{"Child":{}}}
                """, false);
        assertThrows(RuntimeServiceException.class,
                () -> ModelComposer.compose(ContentModelDTO.empty(), List.of(child)));
        ModelDescriptor root = resource("root.json", """
                {"primaryTypes":{"SysContent":{}}}
                """, false);
        SchemaManager manager = ModelComposer.compose(ContentModelDTO.empty(), List.of(child, root));
        assertEquals("SysContent", manager.getDocumentType("Child").getSuperType().getName());
        ModelDescriptor remove = resource("remove.patch.json", """
                [{"op":"remove","path":"/primaryTypes/SysContent"}]
                """, true);
        assertThrows(RuntimeServiceException.class,
                () -> ModelComposer.compose(ContentModelDTO.empty(), List.of(child, root, remove)));
    }

    @Test
    void testDuplicateValueTypesRequirePatch() throws Exception {
        ModelDescriptor first = resource("first.json", """
                {"types":{"shared":{"extends":"string"}}}
                """, false);
        ModelDescriptor same = resource("same.json", """
                {"types":{"shared":{"extends":"string"}}}
                """, false);
        ModelDescriptor different = resource("different.json", """
                {"types":{"shared":{"extends":"long"}}}
                """, false);
        for (ModelDescriptor duplicate : List.of(same, different)) {
            RuntimeServiceException e = assertThrows(RuntimeServiceException.class,
                    () -> ModelComposer.compose(base(), List.of(first, duplicate)));
            assertTrue(e.getMessage().contains(duplicate.src));
            assertTrue(e.getMessage().contains("/types/shared"));
        }
    }

    @Test
    void testRegistryRetainsBundleResourcesAndReplaysUnregistration() throws Exception {
        URL first = resource("first.json", """
                {"mixinTypes":{"First":{}}}
                """, false).resource.toURL();
        URL second = resource("second.json", """
                {"mixinTypes":{"Second":{}}}
                """, false).resource.toURL();
        XMap xmap = new XMap();
        xmap.register(ModelDescriptor.class);
        ModelRegistry registry = new ModelRegistry();
        var element = XMap.getFactory().newDocumentBuilder().newDocument().createElement("model");
        element.setAttribute("src", "model/shared.json");
        xmap.register(registry, context(first), element, "first-bundle");
        xmap.register(registry, context(second), element, "second-bundle");
        assertEquals(2, registry.getModels().size());
        SchemaManager manager = ModelComposer.compose(base(), registry.getModels());
        assertNotNull(manager.getMixin("First"));
        assertNotNull(manager.getMixin("Second"));

        xmap.unregister(registry, "first-bundle");
        assertEquals(1, registry.getModels().size());
        manager = ModelComposer.compose(base(), registry.getModels());
        assertNull(manager.getMixin("First"));
        assertNotNull(manager.getMixin("Second"));
        xmap.register(registry, context(first), element, "first-bundle");
        assertEquals(2, registry.getModels().size());
        assertNotNull(ModelComposer.compose(base(), registry.getModels()).getMixin("First"));
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
        for (String invalid : List.of("", "null", "[]", "{\"schemas\":[]}", "{\"scheams\":{}}", "{\"version\":1}",
                "{\"mixinTypes\":{\"Marker\":{},\"Marker\":{}}}", """
                {"schemas":{"system":{"fields":{"sys_value":{"type":"string","managed":{"systemOwned":[]}}}}}}
                """, "{")) {
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
                [{"op":"unknown","path":"/mixinTypes/Marker","value":{}}]
                """, """
                [{"path":"/mixinTypes/Marker","value":{}}]
                """, """
                [{"op":"add","value":{}}]
                """, """
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
