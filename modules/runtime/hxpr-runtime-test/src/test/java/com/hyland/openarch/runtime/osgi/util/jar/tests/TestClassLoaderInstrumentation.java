/*
 * (C) Copyright 2012-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.runtime.osgi.util.jar.tests;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.JarFile;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hyland.openarch.runtime.test.runner.FeaturesExtension;

/**
 * @author matic
 * @since 5.6
 */
@ExtendWith(FeaturesExtension.class)
public class TestClassLoaderInstrumentation {

    protected JarBuilder jarBuilder;

    @BeforeEach
    public void setupJarBuilder() throws IOException {
        jarBuilder = new JarBuilder();
    }

    @AfterEach
    public void deleteBuiltFiles() {
        jarBuilder.deleteBuiltFiles();
    }

    @Test
    public void canDeleteJar() throws Exception {
        URL firstURL = jarBuilder.buildFirst();
        URL otherURL = jarBuilder.buildOther();
        URL[] jarURLs = new URL[] { firstURL, otherURL };
        try (URLClassLoader ucl = new URLClassLoader(jarURLs, null)) {
            assertNotNull(ucl.loadClass(JarBuilder.First.class.getName()));
            JarFile jarFile = new JarFile(jarURLs[1].getFile());
            jarFile.getManifest();
            jarFile.close();
            File file = new File(jarFile.getName());
            assertTrue(file.delete());
            assertNotNull(ucl.findResource("first.marker"));
            assertNull(ucl.findResource("other.marker"));
        }
    }

}
