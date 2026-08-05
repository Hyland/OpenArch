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

import static java.util.stream.Collectors.toSet;
import static org.nuxeo.ecm.core.schema.MixinNames.SYS_FULL_TEXT_INDEXABLE;
import static org.nuxeo.ecm.core.schema.MixinNames.SYS_GOVERNABLE;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.nuxeo.ecm.core.schema.types.CompositeType;
import org.nuxeo.ecm.core.schema.types.CompositeTypeImpl;
import org.nuxeo.ecm.core.schema.types.Schema;

/**
 * Implementation of a document type.
 */
public class DocumentTypeImpl extends CompositeTypeImpl implements DocumentType {

    protected List<CompositeType> mixins;

    protected Set<String> mixinNames;

    protected Set<String> subtypes;

    /**
     * Constructs a document type. Schemas and mixins must include those from the super type.
     */
    public DocumentTypeImpl(String name, DocumentType superType, List<Schema> schemas, List<CompositeType> mixins,
            String projectId) {
        super(superType, name, schemas, projectId);
        this.mixins = mixins;
        mixinNames = mixins.stream().map(CompositeType::getName).collect(toSet());
    }

    public DocumentTypeImpl(String name) {
        this(name, null, List.of(), List.of(), null);
    }

    public Map<String, Object> toDocumentTypeDTO() {
        String extendz = superType == null ? null : superType.getName();
        Set<String> superMixins = superType == null ? Set.of() : ((DocumentTypeImpl) superType).getMixins();
        Set<String> superSchemas = superType == null ? Set.of() : ((DocumentTypeImpl) superType).getSchemaNames();
        List<CompositeType> localMixins = mixins.stream() //
                                                .filter(m -> !superMixins.contains(m.getName()))
                                                .toList();
        List<String> mixinList = localMixins.stream() //
                                            .map(CompositeType::getName)
                                            .sorted()
                                            .toList();
        Set<String> mixinSchemas = localMixins.stream() //
                                              .flatMap(m -> m.getSchemaNames().stream())
                                              .collect(toSet());
        List<String> schemaList = schemas.keySet() //
                                         .stream()
                                         .filter(s -> !superSchemas.contains(s) && !mixinSchemas.contains(s))
                                         .sorted()
                                         .toList();
        List<String> subtypeList = subtypes == null ? List.of() : subtypes.stream().sorted().toList();

        Map<String, Object> map = new LinkedHashMap<>();
        if (projectId != null) {
            map.put("projectId", projectId);
        }
        if (extendz != null) {
            map.put("extends", extendz);
        }
        if (!schemaList.isEmpty()) {
            map.put("schemas", schemaList);
        }
        if (!mixinList.isEmpty()) {
            map.put("mixins", mixinList);
        }
        if (!subtypeList.isEmpty()) {
            map.put("subtypes", subtypeList);
        }
        return map;
    }

    @Override
    public boolean isFile() {
        return mixinNames.contains(MixinNames.SYS_FILISH);
    }

    @Override
    public boolean isFolder() {
        return mixinNames.contains(MixinNames.SYS_FOLDERISH);
    }

    @Override
    public boolean isOrdered() {
        return mixinNames.contains(MixinNames.SYS_ORDERABLE);
    }

    @Override
    public Set<String> getMixins() {
        return mixinNames;
    }

    @Override
    public boolean hasMixin(String mixin) {
        return mixinNames.contains(mixin);
    }

    @Override
    public Set<String> getSubtypes() {
        return subtypes;
    }

    @Override
    public void setSubtypes(Collection<String> subtypes) {
        if (subtypes == null) {
            this.subtypes = Collections.emptySet();
        } else {
            this.subtypes = new HashSet<>(subtypes);
        }
    }

    @Override
    public boolean hasSubtype(String subtype) {
        return subtypes.contains(subtype);
    }

    @Override
    public boolean isFullTextIndexable() {
        return mixinNames.contains(SYS_FULL_TEXT_INDEXABLE);
    }

    @Override
    public boolean isGovernable() {
        return mixinNames.contains(SYS_GOVERNABLE);
    }
}
