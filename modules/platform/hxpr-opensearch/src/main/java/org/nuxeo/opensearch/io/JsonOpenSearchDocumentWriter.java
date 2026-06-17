/*
 * (C) Copyright 2014-2021 Nuxeo (http://nuxeo.com/) and others.
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
 *     Benoit Delbosc
 *     Florent Guillaume
 */
package org.nuxeo.opensearch.io;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ANCESTOR_IDS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_CHANGE_TOKEN;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_FULLTEXT_BINARY;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_IS_FOLDERISH;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_LIFECYCLE_STATE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_LOCK_CREATED;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_LOCK_OWNER;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_MIXIN_TYPES;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_NAME;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PARENT_ID;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PARENT_PATH;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PATH;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PATH_DEPTH;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PATH_LEVEL;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_POS;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PRIMARY_TYPE;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_READ_ACL;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_REPOSITORY;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_SECURITY_POLICIES;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.Lock;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.ValuesVisitor;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.schema.types.primitives.BooleanType;
import org.nuxeo.ecm.core.schema.types.primitives.DecimalType;
import org.nuxeo.ecm.core.schema.types.primitives.DoubleType;
import org.nuxeo.ecm.core.schema.types.primitives.LongType;

import jakarta.json.stream.JsonGenerator;

/**
 * JSon writer that outputs a format ready to eat by opensearch.
 *
 * @since 5.9.3
 */
public class JsonOpenSearchDocumentWriter {

    private final JsonGenerator jg;

    public JsonOpenSearchDocumentWriter(JsonGenerator jg) {
        this.jg = jg;
    }

    public void write(DocumentModel doc) {
        jg.writeStartObject();
        writeSystemProperties(doc);
        writeProperties(doc);
        jg.writeEnd();
        jg.flush();
    }

    /**
     * @since 7.2
     */
    protected void writeSystemProperties(DocumentModel doc) {
        String docId = doc.getId();
        jg.write(SYS_REPOSITORY, doc.getRepositoryName());
        jg.write(SYS_ID, docId);
        jg.write(SYS_NAME, doc.getName());

        DocumentRef parentRef = doc.getParentRef();
        if (parentRef != null) {
            String parentPath = doc.getCoreSession().getDocument(parentRef).getPathAsString();
            jg.write(SYS_PARENT_ID, parentRef.toString());
            jg.write(SYS_PARENT_PATH, parentPath);
        }
        String path = doc.getPathAsString();
        jg.write(SYS_PATH, path);
        if (isNotBlank(path)) {
            String[] split = path.split("/");
            if (split.length > 0) {
                for (int i = 1; i < split.length; i++) {
                    jg.write(SYS_PATH_LEVEL + i, split[i]);
                }
            }
            jg.write(SYS_PATH_DEPTH, split.length);
            jg.writeStartArray(SYS_ANCESTOR_IDS);
            doc.getAncestorIds().forEach(jg::write);
            jg.writeEnd();
        }

        jg.write(SYS_PRIMARY_TYPE, doc.getType());

        jg.writeStartArray(SYS_MIXIN_TYPES);
        List<String> mixins = new ArrayList<>(doc.getEffectiveMixins());
        mixins.sort(null);
        for (String mixin : mixins) {
            jg.write(mixin);
        }
        jg.writeEnd();

        // jg.writeArrayFieldStart(SYS_ANCESTOR_IDS);
        // for (String id : doc.getAncesorIds()) {
        // jg.writeString(id);
        // }
        // jg.writeEndArray();
        jg.write(SYS_IS_FOLDERISH, doc.isFolder());

        String lifecycleState = getLifecycleState(doc);
        if (lifecycleState != null) {
            jg.write(SYS_LIFECYCLE_STATE, lifecycleState);
        }
        Lock lock = doc.getLockInfo();
        if (lock != null) {
            Instant created = lock.getCreated();
            if (created != null) {
                jg.write(SYS_LOCK_CREATED, created.toString());
            }
            String owner = lock.getOwner();
            if (owner != null) {
                jg.write(SYS_LOCK_OWNER, owner);
            }
        }
        String changeToken = doc.getChangeToken();
        if (changeToken != null) {
            jg.write(SYS_CHANGE_TOKEN, changeToken);
        }

        Long pos = doc.getPos();
        if (pos != null) {
            jg.write(SYS_POS, pos.longValue());
        }

        List<String> securityPolicies = doc.getSecurityPolicies();
        if (!securityPolicies.isEmpty()) {
            securityPolicies = new ArrayList<>(securityPolicies);
            securityPolicies.sort(null);
            jg.writeStartArray(SYS_SECURITY_POLICIES);
            for (String securityPolicy : securityPolicies) {
                jg.write(securityPolicy);
            }
            jg.writeEnd();
        }

        Set<String> racls = new TreeSet<>(getReadAcl(doc)); // unique, sorted
        jg.writeStartArray(SYS_READ_ACL);
        for (String principal : racls) {
            jg.write(principal);
        }
        jg.writeEnd();

        writeFulltextBinary(doc);
    }

    /**
     * Writes the fulltext binary content.
     * Override this method to exclude fulltext from denormalized data.
     *
     * @param doc the document
     */
    protected void writeFulltextBinary(DocumentModel doc) {
        String fulltext = doc.getBinaryFulltext();
        if (isNotBlank(fulltext)) {
            jg.write(SYS_FULLTEXT_BINARY, fulltext);
        }
    }

    private String getLifecycleState(DocumentModel doc) {
        return (String) doc.getCoreSession().getDocumentSystemProp(doc.getRef(), SYS_LIFECYCLE_STATE, String.class);
    }

    private List<String> getReadAcl(DocumentModel doc) {
        return (List<String>) doc.getCoreSession().getDocumentSystemProp(doc.getRef(), SYS_READ_ACL, List.class);
    }

    protected void writeProperties(DocumentModel doc) {
        try {
            doc.visitValues(new JsonGeneratorValuesVisitor(jg));
        } catch (ClassCastException e) {
            throw new NuxeoException("writing JSON property failed on document: %s".formatted(doc), e);
        }
    }

    public static class JsonGeneratorValuesVisitor implements ValuesVisitor {

        private final JsonGenerator jg;

        private int depth;

        public JsonGeneratorValuesVisitor(JsonGenerator jg) {
            this.jg = jg;
        }

        @Override
        public boolean visitActualBlobs() {
            return false;
        }

        @Override
        public String getXPath() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Comparator<String> keySorter() {
            return null;
        }

        @Override
        public void startObject() {
            if (depth++ != 0) {
                jg.writeStartObject();
            }
        }

        @Override
        public void endObject() {
            if (--depth != 0) {
                jg.writeEnd();
            }
        }

        @Override
        public void startObjectElement(Field field) {
            jg.writeKey(field.getName());
        }

        @Override
        public void endObjectElement() {
            // nothing
        }

        @Override
        public void startList() {
            jg.writeStartArray();
        }

        @Override
        public void endList() {
            jg.writeEnd();
        }

        @Override
        public void startListElement(int i) {
            // nothing
        }

        @Override
        public void endListElement() {
            // nothing
        }

        @Override
        public void value(Object value, Field field) {
            Type type = field.getType();
            if (type instanceof BooleanType) {
                jg.write((Boolean) value);
            } else if (type instanceof LongType) {
                jg.write(((Number) value).longValue());
            } else if (type instanceof DoubleType) {
                jg.write(((Number) value).doubleValue());
            } else if (type instanceof DecimalType) {
                jg.write((BigDecimal) value);
            } else {
                jg.write(type.encode(value));
            }
        }

        @Override
        public void list(List<Object> list, Field listField, Field elemField) {
            jg.writeStartArray();
            for (Object value : list) {
                value(value, elemField);
            }
            jg.writeEnd();
        }

        @Override
        public void blob(Blob blob, Object state) {
            throw new UnsupportedOperationException();
        }
    }

}
