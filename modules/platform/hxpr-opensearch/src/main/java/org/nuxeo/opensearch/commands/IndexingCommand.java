/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Thierry Delprat
 *     Benoit Delbosc
 */
package org.nuxeo.opensearch.commands;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.nuxeo.ecm.core.api.DocumentModel;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Holds information about what type of indexing operation must be processed. IndexingCommands are create "on the fly"
 * via a Synchronous event listener and at post commit time the system will merge the commands and execute worker to
 * process them.
 */
public class IndexingCommand implements Serializable {

    private static final Logger log = LogManager.getLogger(IndexingCommand.class);

    private static final long serialVersionUID = 1L;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public enum Type {
        INSERT, UPDATE, UPDATE_SECURITY, DELETE, UPDATE_DIRECT_CHILDREN,
    }

    public static final String PREFIX = "IxCd-";

    protected String id;

    protected Type type;

    protected boolean sync;

    protected boolean recurse;

    protected String targetDocumentId;

    protected String path;

    protected String repositoryName;

    protected List<String> schemas;

    protected long order;

    protected static AtomicLong seq = new AtomicLong(0);

    @JsonCreator
    protected IndexingCommand() {
    }

    /**
     * Create an indexing command
     *
     * @param document the target document
     * @param commandType the type of command
     * @param sync if true the command will be processed on the same thread after transaction completion and the
     *            OpenSearch index will be refresh
     * @param recurse the command affect the document and all its descendants
     */
    public IndexingCommand(DocumentModel document, Type commandType, boolean sync, boolean recurse) {
        id = PREFIX + seq.incrementAndGet();
        type = commandType;
        this.sync = sync;
        this.recurse = recurse;
        if ((sync && recurse) && commandType != Type.DELETE) {
            // we don't want sync and recursive command
            throw new IllegalArgumentException(
                    "Recurse and synchronous command is not allowed: cmd: " + this + ", doc: " + document);
        }
        if (document == null) {
            throw new IllegalArgumentException("Target document is null for: " + this);
        }
        repositoryName = document.getRepositoryName();
        targetDocumentId = document.getId();
        path = document.getPathAsString();
        if (targetDocumentId == null) {
            throw new IllegalArgumentException("Target document has a null uid: " + this);
        }
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public String getPath() {
        return path;
    }

    /**
     * @return true if merged
     */
    public boolean merge(IndexingCommand other) {
        if (canBeMerged(other)) {
            merge(other.sync, other.recurse);
            return true;
        }
        return false;
    }

    protected void merge(boolean sync, boolean recurse) {
        this.sync = this.sync || sync;
        this.recurse = this.recurse || recurse;
    }

    protected boolean canBeMerged(IndexingCommand other) {
        if (type != other.type) {
            return false;
        }
        if (type == Type.DELETE) {
            // we support recursive sync deletion
            return true;
        }
        // only if the result is not a sync and recurse command
        return !((other.sync || sync) && (other.recurse || recurse));
    }

    public boolean isSync() {
        return sync;
    }

    public boolean isRecurse() {
        return recurse;
    }

    public Type getType() {
        return type;
    }

    public String toJSON() throws IOException {
        StringWriter out = new StringWriter();
        JsonGenerator jsonGen = MAPPER.createGenerator(out);
        toJSON(jsonGen);
        out.flush();
        jsonGen.close();
        return out.toString();
    }

    public void toJSON(JsonGenerator jsonGen) {
        jsonGen.writeStartObject();
        jsonGen.writeStringProperty("id", id);
        jsonGen.writeStringProperty("type", String.format("%s", type));
        jsonGen.writeStringProperty("docId", getTargetDocumentId());
        jsonGen.writeStringProperty("path", path);
        jsonGen.writeStringProperty("repo", getRepositoryName());
        jsonGen.writeBooleanProperty("recurse", recurse);
        jsonGen.writeBooleanProperty("sync", sync);
        jsonGen.writeNumberProperty("order", getOrder());
        jsonGen.writeEndObject();
    }

    /**
     * Create a command from a JSON string.
     *
     * @throws IllegalArgumentException if json is invalid or command is invalid
     */
    public static IndexingCommand fromJSON(String json) {
        try {
            return fromJSON(MAPPER.readTree(json));
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Invalid JSON: " + json, e);
        }
    }

    public static IndexingCommand fromJSON(JsonNode jsonNode) {
        IndexingCommand cmd = new IndexingCommand();
        for (Map.Entry<String, JsonNode> field : jsonNode.properties()) {
            String key = field.getKey();
            JsonNode value = field.getValue();
            if (value.isNull()) {
                continue;
            }
            if ("type".equals(key)) {
                cmd.type = Type.valueOf(value.stringValue());
            } else if ("docId".equals(key)) {
                cmd.targetDocumentId = value.stringValue();
            } else if ("path".equals(key)) {
                cmd.path = value.stringValue();
            } else if ("repo".equals(key)) {
                cmd.repositoryName = value.stringValue();
            } else if ("id".equals(key)) {
                cmd.id = value.stringValue();
            } else if ("recurse".equals(key)) {
                cmd.recurse = value.booleanValue();
            } else if ("sync".equals(key)) {
                cmd.sync = value.booleanValue();
            }
        }
        if (cmd.targetDocumentId == null) {
            throw new IllegalArgumentException("Document uid is null: " + cmd);
        }
        if (cmd.type == null) {
            throw new IllegalArgumentException("Invalid type: " + cmd);
        }
        return cmd;
    }

    public String getId() {
        return id;
    }

    public String getTargetDocumentId() {
        return targetDocumentId;
    }

    public IndexingCommand clone(DocumentModel newDoc) {
        return new IndexingCommand(newDoc, type, sync, recurse);
    }

    public String[] getSchemas() {
        String[] ret = null;
        if (schemas != null && !schemas.isEmpty()) {
            ret = schemas.toArray(new String[schemas.size()]);
        }
        return ret;
    }

    public void addSchemas(String schema) {
        if (schemas == null) {
            schemas = new ArrayList<>();
        }
        if (!schemas.contains(schema)) {
            schemas.add(schema);
        }
    }

    @Override
    public String toString() {
        try {
            return toJSON();
        } catch (IOException | JacksonException _) {
            return super.toString();
        }
    }

    /**
     * Try to make the command synchronous. Recurse command will stay in async for update.
     */
    public void makeSync() {
        if (!sync && (!recurse || type == Type.DELETE)) {
            sync = true;
            if (log.isDebugEnabled()) {
                log.debug("Turn command into sync: {}", toString());
            }
        }
    }

    // @since 8.3
    public long getOrder() {
        return order;
    }

    // @since 8.3
    public void setOrder(long order) {
        this.order = order;
    }
}
