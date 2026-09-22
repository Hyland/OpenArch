/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
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

package org.nuxeo.ecm.core.schema;

import java.io.File;

import com.hyland.openarch.common.xmap.Resource;
import com.hyland.openarch.common.xmap.annotation.XNode;
import com.hyland.openarch.common.xmap.annotation.XObject;
import com.hyland.openarch.common.xmap.registry.XEnable;
import com.hyland.openarch.common.xmap.registry.XRegistry;
import com.hyland.openarch.common.xmap.registry.XRegistryId;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
@XObject("schema")
@XRegistry(enable = false, merge = false)
public class SchemaBindingDescriptor {

    @XNode("@name")
    @XRegistryId
    public String name;

    @XNode("@src")
    public Resource src;

    private File file;

    @XNode("@prefix")
    public String prefix = "";

    @XNode(value = "@override")
    public boolean override = false;

    @XNode("@isVersionWritable")
    public boolean isVersionWritable = false;

    @XNode(value = XEnable.ENABLE, fallback = "@enabled")
    @XEnable
    public Boolean enabled;

    @XNode("@projectId")
    public String projectId;

    @Override
    public String toString() {
        return "Schema: " + name;
    }

    public File getFile() {
        return this.file;
    }

    public void setFile(File file) {
        this.file = file;
    }

}
