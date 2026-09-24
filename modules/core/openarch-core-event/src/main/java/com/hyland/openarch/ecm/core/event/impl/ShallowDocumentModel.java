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
package com.hyland.openarch.ecm.core.event.impl;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.hyland.openarch.common.utils.Path;
import com.hyland.openarch.ecm.core.api.CoreSession;
import com.hyland.openarch.ecm.core.api.DocumentModel;
import com.hyland.openarch.ecm.core.api.DocumentRef;
import com.hyland.openarch.ecm.core.api.UpdateCondition;
import com.hyland.openarch.ecm.core.api.ValuesVisitor;
import com.hyland.openarch.ecm.core.api.IdRef;
import com.hyland.openarch.ecm.core.api.Lock;
import com.hyland.openarch.ecm.core.api.RepositoryPrincipal;
import com.hyland.openarch.ecm.core.api.PathRef;
import com.hyland.openarch.ecm.core.api.PropertyException;
import com.hyland.openarch.ecm.core.api.model.resolver.DocumentPropertyObjectResolverImpl;
import com.hyland.openarch.ecm.core.api.model.resolver.PropertyObjectResolver;
import com.hyland.openarch.ecm.core.api.security.ACL;
import com.hyland.openarch.ecm.core.event.Event;
import com.hyland.openarch.ecm.core.event.EventBundle;
import com.hyland.openarch.ecm.core.model.InternalDocument.BlobAccessor;
import com.hyland.openarch.ecm.core.schema.DocumentType;
import com.hyland.openarch.ecm.core.schema.MixinNames;

/**
 * Light weight {@link DocumentModel} implementation Only holds {@link DocumentRef}, RepositoryName, name, path and
 * context data. Used to reduce memory footprint of {@link Event} stacked in {@link EventBundle}.
 *
 * @author Thierry Delprat
 */
public class ShallowDocumentModel implements DocumentModel {

    private static final long serialVersionUID = 1L;

    private final String id;

    private final String repoName;

    private final String name;

    private final Path path;

    private final String type;

    private final boolean isFolder;

    private final boolean isVersion;

    private final Map<String, Serializable> contextData;

    private final Set<String> mixins;

    public ShallowDocumentModel(DocumentModel doc) {
        id = doc.getId();
        repoName = doc.getRepositoryName();
        name = doc.getName();
        path = doc.getPath();
        type = doc.getType();
        isFolder = doc.isFolder();
        isVersion = doc.isVersion();
        contextData = new HashMap<>(doc.getContextData());
        mixins = doc.getEffectiveMixins();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DocumentRef getRef() {
        return id == null ? null : new IdRef(id);
    }

    @Override
    public String getRepositoryName() {
        return repoName;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Long getPos() {
        return null;
    }

    @Override
    public Path getPath() {
        return path;
    }

    @Override
    public String getPathAsString() {
        if (path != null) {
            return path.toString();
        }
        return null;
    }

    @Override
    public DocumentRef getParentRef() {
        if (path != null) {
            return new PathRef(path.removeLastSegments(1).toString());
        }
        return null;
    }

    @Override
    public List<String> getAncestorIds() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public boolean isTrashed() {
        return false;
    }

    @Override
    public boolean isFolder() {
        return isFolder;
    }

    @Override
    public boolean isVersion() {
        return isVersion;
    }

    @Override
    public void copyContextData(DocumentModel otherDocument) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setUpdateCondition(UpdateCondition condition) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ACL getLocalACL() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ACL getEffectiveACL() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> getEffectivePermissions() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, Serializable> getContextData() {
        return contextData;
    }

    @Override
    public CoreSession getCoreSession() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void create() {
        // nothing
    }

    @Override
    public Set<String> getEffectiveMixins() {
        return mixins;
    }

    @Override
    public void setPrimaryTypeAndMixinTypes(String typeName, Collection<String> mixins) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setMixins(Collection<String> mixins) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addMixin(String mixin) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> getSchemas() {
        throw new UnsupportedOperationException();
    }

    @Override
    public DocumentType getDocumentType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getPropertyValue(String xpath) throws PropertyException {
        throw new UnsupportedOperationException();
    }

    @Override
    public RepositoryPrincipal getPrincipal() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Long getVersion() {
        return null;
    }

    @Override
    public Long getMinorVersion() {
        return null;
    }

    @Override
    public boolean hasMixin(String mixin) {
        return mixins.contains(mixin);
    }

    @Override
    public boolean hasSchema(String schema) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isLocked() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isDirty() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isVersionable() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isGovernable() {
        return hasMixin(MixinNames.SYS_GOVERNABLE);
    }

    @Override
    public void putContextData(String key, Serializable value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void refresh() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void refresh(int refreshFlags, String[] schemas) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void reset() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setACL(ACL acl, boolean overwrite) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Lock setLock() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Lock getLockInfo() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Lock removeLock() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setPropertyValue(String xpath, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitValues(ValuesVisitor visitor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitBlobs(Consumer<BlobAccessor> blobVisitor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitFields(ValuesVisitor visitor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DocumentModel clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    @Override
    public Serializable getContextData(String key) {
        if (contextData == null) {
            return null;
        }
        return contextData.get(key);
    }

    @Override
    public boolean isCheckedOut() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void checkOut() {
        throw new UnsupportedOperationException();
    }

    @Override
    public DocumentRef checkIn() {
        throw new UnsupportedOperationException();
    }

    @Override
    public DocumentRef checkIn(boolean minor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getChangeToken() {
        return null;
    }

    @Override
    public PropertyObjectResolver getObjectResolver(String xpath) {
        return DocumentPropertyObjectResolverImpl.create(this, xpath);
    }

}
