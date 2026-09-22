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

import static org.nuxeo.ecm.core.schema.SchemaManagerConstants.SYSTEM_SCHEMA;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.Environment;
import org.nuxeo.ecm.core.schema.types.CompositeType;
import org.nuxeo.ecm.core.schema.types.CompositeTypeImpl;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.schema.types.TypeException;
import org.nuxeo.runtime.RuntimeServiceException;
import org.xml.sax.SAXException;

/**
 * Schema Manager implementation initialized through XML and XSD contributions.
 */
public class SchemaManagerImpl extends SchemaManagerBase {

    private static final Logger log = LogManager.getLogger(SchemaManagerImpl.class);

    private File schemaDir;

    protected Set<String> disabledSchemas = new HashSet<>();

    protected Set<String> disabledMixins = new HashSet<>();

    public static final String SCHEMAS_DIR_NAME = "schemas";

    public SchemaManagerImpl(SchemaRegistry schemaRegistry, DocTypeRegistry doctypeRegistry) {
        schemaDir = new File(Environment.getDefault().getTemp(), SCHEMAS_DIR_NAME);
        if (!schemaDir.mkdirs() && !schemaDir.exists()) {
            throw new RuntimeServiceException("Unable to create schemas directory");
        }
        clearSchemaDir();

        this.disabledSchemas = schemaRegistry.getDisabledSchemas();
        computeSchemas(schemaRegistry.getSchemas());
        this.disabledMixins = doctypeRegistry.getDisabledMixins();
        computeMixins(doctypeRegistry.getMixins()); // depends on schemas
        computeDocumentTypes(doctypeRegistry.getDocumentTypes()); // depends on schemas and mixins

        computeDenormalizations();
    }

    protected SchemaManagerImpl() {
        // for tests
    }

    /*
     * ===== Types =====
     */

    // called from XSDLoader
    protected File getSchemasDir() {
        return schemaDir;
    }

    // called from XSDLoader
    protected void registerType(Type type) {
        String name = type.getName();
        Type previous = types.put(name, type);
        if (previous != null && previous != type) {
            throw new RuntimeServiceException("Type " + name + " defined by " + type.getSuperType()
                    + " was previously defined by " + previous.getSuperType());
        }
    }

    // called from XSDLoader
    protected void unregisterType(Type type) {
        String name = type.getName();
        if (types.remove(name) != type) {
            throw new IllegalStateException(name);
        }
    }

    /*
     * ===== Schemas =====
     */

    private void clearSchemaDir() {
        try {
            org.apache.commons.io.FileUtils.cleanDirectory(schemaDir);
        } catch (IOException e) {
            throw new RuntimeServiceException(e);
        }
    }

    private void computeSchemas(Map<String, SchemaBindingDescriptor> resolvedSchemas) {
        schemas.clear();
        RuntimeException errors = new RuntimeException("Cannot load schemas");

        for (SchemaBindingDescriptor sd : resolvedSchemas.values()) {
            try {
                copySchema(sd);
            } catch (IOException error) {
                errors.addSuppressed(error);
            }
        }
        for (SchemaBindingDescriptor sd : resolvedSchemas.values()) {
            try {
                loadSchema(sd);
            } catch (IOException | SAXException | TypeException error) {
                errors.addSuppressed(error);
            }
        }
        if (errors.getSuppressed().length > 0) {
            throw errors;
        }
    }

    private void copySchema(SchemaBindingDescriptor sd) throws IOException {
        if (sd.src == null) {
            return;
        }
        URL url = sd.src.toURL();
        if (url == null) {
            log.error("XSD Schema not found: {}", sd.src);
            return;
        }
        try (InputStream in = url.openStream()) {
            sd.setFile(new File(schemaDir, sd.name + ".xsd"));
            FileUtils.copyInputStreamToFile(in, sd.getFile()); // may overwrite
        }
    }

    private void loadSchema(SchemaBindingDescriptor sd) throws IOException, SAXException, TypeException {
        if (sd.getFile() == null) {
            // INLINE Schemas ARE NOT YET IMPLEMENTED!
            return;
        }
        if (sd.prefix.isEmpty()) {
            // always use a prefix
            sd.prefix = sd.name;
        }
        // loadSchema calls this.registerSchema
        XSDLoader schemaLoader = new XSDLoader(this, sd);
        schemaLoader.loadSchema(sd.name, sd.prefix, sd.getFile(), sd.isVersionWritable);
        log.debug("Registered schema: {} from {}", sd.name, sd.getFile());
    }

    /*
     * ===== Mixins =====
     */

    private void computeMixins(List<MixinDescriptor> allMixins) {
        mixins.clear();
        noPerDocumentQueryMixins.clear();
        for (MixinDescriptor fd : allMixins) {
            Set<String> fdSchemas = SchemaDescriptor.getSchemaNames(fd.schemas);
            registerMixin(fd.name, fdSchemas);
            if (Boolean.FALSE.equals(fd.perDocumentQuery)) {
                noPerDocumentQueryMixins.add(fd.name);
            }
        }
    }

    // also called when a document type references an unknown mixin (WARN)
    private CompositeType registerMixin(String name, Set<String> schemaNames) {
        List<Schema> mixinSchemas = new ArrayList<>(schemaNames.size());
        for (String schemaName : schemaNames) {
            Schema schema = schemas.get(schemaName);
            if (schema == null) {
                if (disabledSchemas.contains(schemaName)) {
                    // schema is disabled, don't log as ERROR
                    log.debug("Mixin: {} uses disabled schema: {}", name, schemaName);
                    continue;
                }
                log.error("Mixin: {} uses unknown schema: {}", name, schemaName);
                continue;
            }
            mixinSchemas.add(schema);
        }
        CompositeType ct = new CompositeTypeImpl(null, name, mixinSchemas, null);
        mixins.put(ct.getName(), ct);
        return ct;
    }

    /*
     * ===== Document types =====
     */

    private void computeDocumentTypes(Map<String, DocumentTypeDescriptor> dtds) {
        // recompute all types, parents first
        documentTypes.clear();

        Schema systemSchema = schemas.get(SYSTEM_SCHEMA);
        // The schema module can be used standalone for tests without the hxpr-core system schema contribution.
        List<Schema> systemSchemas = systemSchema == null ? List.of() : List.of(systemSchema);
        registerDocumentType(
                new DocumentTypeImpl(TypeConstants.SYS_CONTENT, null, systemSchemas, List.of(), null)); // SysContent
        for (String name : dtds.keySet()) {
            LinkedHashSet<String> stack = new LinkedHashSet<>();
            recomputeDocumentType(name, stack, dtds);
        }
    }

    private DocumentType recomputeDocumentType(String name, Set<String> stack,
            Map<String, DocumentTypeDescriptor> dtds) {
        DocumentTypeImpl docType = documentTypes.get(name);
        if (docType != null) {
            // already done
            return docType;
        }
        if (stack.contains(name)) {
            log.error("Document type: {} used in parent inheritance loop: {}", name, stack);
            return null;
        }
        DocumentTypeDescriptor dtd = dtds.get(name);
        if (dtd == null) {
            log.error("Document type: {} does not exist, used as parent by type: {}", name, stack);
            return null;
        }

        // find and recompute the parent first
        DocumentType parent;
        String parentName = dtd.superTypeName;
        if (parentName == null) {
            parent = null;
        } else {
            parent = documentTypes.get(parentName);
            if (parent == null) {
                stack.add(name);
                parent = recomputeDocumentType(parentName, stack, dtds);
                stack.remove(name);
            }
        }

        return recomputeDocumentType(name, dtd, parent);
    }

    private DocumentType recomputeDocumentType(String name, DocumentTypeDescriptor dtd, DocumentType parent) {
        // find the mixins and schemas names
        Set<String> mixinNames = new HashSet<>(Arrays.asList(dtd.mixins));
        Set<String> schemaNames = SchemaDescriptor.getSchemaNames(dtd.schemas);
        Set<String> subtypes = new HashSet<>(Arrays.asList(dtd.subtypes));

        // inherited
        if (parent != null) {
            mixinNames.addAll(parent.getMixins());
            schemaNames.addAll(parent.getSchemaNames());
        }

        // add schemas names from mixins
        for (String mixinName : mixinNames) {
            CompositeType ct = mixins.get(mixinName);
            if (ct == null) {
                if (disabledMixins.contains(mixinName)) {
                    // mixin is disabled, don't WARN about it
                    log.debug("Disabled mixin: {} used in document type: {}", mixinName, name);
                    continue;
                }
                log.warn("Undeclared mixin: {} used in document type: {}", mixinName, name);
                // register it with no schemas
                ct = registerMixin(mixinName, Collections.emptySet());
            }
            schemaNames.addAll(ct.getSchemaNames());
        }
        mixinNames.removeAll(disabledMixins);
        List<CompositeType> mixins = mixinNames.stream() //
                                               .map(this::getMixin)
                                               .filter(Objects::nonNull)
                                               .toList();

        // find the schemas
        List<Schema> docTypeSchemas = new ArrayList<>();
        for (String schemaName : schemaNames) {
            Schema schema = schemas.get(schemaName);
            if (schema == null) {
                if (disabledSchemas.contains(schemaName)) {
                    // schema is disabled, don't log as ERROR
                    log.debug("Document type: {} uses disabled schema: {}", name, schemaName);
                    continue;
                }
                log.error("Document type: {} uses unknown schema: {}", name, schemaName);
                continue;
            }
            docTypeSchemas.add(schema);
        }

        // create doctype
        DocumentTypeImpl docType = new DocumentTypeImpl(name, parent, docTypeSchemas, mixins, null);
        docType.setSubtypes(subtypes);
        registerDocumentType(docType);

        return docType;
    }

    private void registerDocumentType(DocumentTypeImpl docType) {
        documentTypes.put(docType.getName(), docType);
    }

}
