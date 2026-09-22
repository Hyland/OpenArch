/*
 * (C) Copyright 2017-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @since 9.10
 */
public class TestJarUtils {

    public static final String BUILT_JAR_NAME = "built-jar.jar";

    private Path resourcesRoot;

    @BeforeEach
    public void setUp() throws Exception {
        resourcesRoot = Paths.get(getClass().getResource("/").toURI());
    }

    @AfterEach
    public void tearDown() throws Exception {
        Files.deleteIfExists(resourcesRoot.resolve(BUILT_JAR_NAME));
    }

    @Test
    public void testZipDirectory() throws Exception {
        Path source = resourcesRoot.resolve("TestJarUtils");
        Path target = resourcesRoot.resolve(BUILT_JAR_NAME);
        target = JarUtils.zipDirectory(source, target);

        Manifest manifest = JarUtils.getManifest(target.toFile());
        assertNotNull(manifest);
        Attributes mainAttributes = manifest.getMainAttributes();
        assertNotNull(mainAttributes);
        assertEquals("1.0", mainAttributes.getValue("Manifest-Version"));
        assertEquals("Nuxeo JarUtils test", mainAttributes.getValue("Bundle-Name"));
        assertEquals("2", mainAttributes.getValue("Bundle-ManifestVersion"));
        assertEquals("com.hyland.openarch.common.jarutils.test;singleton:=true", mainAttributes.getValue("Bundle-SymbolicName"));
    }

}
