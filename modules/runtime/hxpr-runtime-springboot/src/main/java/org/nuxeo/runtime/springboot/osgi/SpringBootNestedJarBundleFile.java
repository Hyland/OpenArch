/*
 * (C) Copyright 2024 Hyland (https://hyland.com/) and others.
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
 *     dedwards
 */
package org.nuxeo.runtime.springboot.osgi;

import org.nuxeo.osgi.NestedJarBundleFile;
import org.springframework.boot.loader.jar.NestedJarFile;

/**
 * Represents a nested JAR bundle file in a Spring Boot application. Extends the {@link NestedJarBundleFile} class.
 *
 * @see https://docs.spring.io/spring-boot/specification/executable-jar/jarfile-class.html
 */
public class SpringBootNestedJarBundleFile extends NestedJarBundleFile {

    /**
     * Constructs a new instance of the {@code SpringBootNestedJarBundleFile} class with the specified location and
     * nested JAR file. Converts the urlBase to the format expected by Spring Boot.
     *
     * @param location The location of the nested JAR bundle file.
     * @param jarFile The nested JAR file.
     * @see https://docs.spring.io/spring-boot/specification/executable-jar/jarfile-class.html
     */
    public SpringBootNestedJarBundleFile(String location, NestedJarFile jarFile) {
        super(location, jarFile);
        urlBase = "jar:nested:" + jarFile.getName().replace("!/", "/!") + "!/";
    }

}
