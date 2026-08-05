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
package org.nuxeo.ecm.core.storage.dbs;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import com.hyland.hxpr.runtime.pubsub.SerializableAccumulableInvalidations;
import org.apache.commons.io.IOUtils;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.cluster.ClusterService;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * A set of invalidations for a given repository.
 * <p>
 * Records both modified and deleted fragments, as well as "parents modified" fragments.
 *
 * @since 8.10
 */
public class DBSInvalidations implements SerializableAccumulableInvalidations {

    private static final ObjectMapper MAPPER = new ObjectMapper().rebuild()
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /**
     * Maximum number of invalidations kept, after which only {@link #all} is set. This avoids accumulating too many
     * invalidations in memory, at the expense of more coarse-grained invalidations.
     */
    public static final int MAX_SIZE = 10000;

    /**
     * Used locally when invalidating everything, or when too many invalidations have been received.
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public boolean all;

    /** null when empty */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Set<String> ids;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    protected String nodeId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    protected Long timestamp;

    public DBSInvalidations() {
    }

    public DBSInvalidations(boolean all) {
        this.all = all;
    }

    public void setClusterProperties() {
        this.nodeId = Framework.getService(ClusterService.class).getNodeId();
        var timestampTransaction = TransactionHelper.getTransactionStartTime();
        if (timestampTransaction != null) {
            this.timestamp = timestampTransaction.toEpochMilli();
        }
    }

    @Override
    public boolean isEmpty() {
        return ids == null && !all;
    }

    public void clear() {
        all = false;
        ids = null;
        nodeId = null;
        timestamp = null;
    }

    protected void setAll() {
        all = true;
        ids = null;
    }

    protected void checkMaxSize() {
        if (ids != null && ids.size() > MAX_SIZE) {
            setAll();
        }
    }

    @Override
    public void add(SerializableAccumulableInvalidations o) {
        DBSInvalidations other = (DBSInvalidations) o;
        if (other == null) {
            return;
        }
        if (all) {
            return;
        }
        if (other.all) {
            setAll();
            return;
        }
        if (other.ids != null) {
            if (ids == null) {
                ids = new HashSet<>();
            }
            ids.addAll(other.ids);
        }
        checkMaxSize();
    }

    public void add(String id) {
        if (all) {
            return;
        }
        if (ids == null) {
            ids = new HashSet<>();
        }
        ids.add(id);
        checkMaxSize();
    }

    @Override
    public DBSInvalidations minimize() {
        if (all) {
            // already at minimal size
            return this;
        } else {
            DBSInvalidations other = new DBSInvalidations(true); // all = true
            other.setNodeId(nodeId);
            other.setTimestamp(timestamp);
            return other;
        }
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public void addAll(Collection<String> invsToAdd) {
        if (all) {
            return;
        }
        if (ids == null) {
            ids = new HashSet<>(invsToAdd);
        } else {
            ids.addAll(invsToAdd);
        }
        checkMaxSize();
    }

    private static final int ALL_IDS = (byte) 'A';

    private static final int ID_SEP = (byte) ',';

    private static final int ID_JSON = (byte) '{';

    @Override
    public void serialize(Writer writer) throws IOException {
        MAPPER.writeValue(writer, this);
    }

    public static DBSInvalidations deserialize(InputStream in) throws IOException {
        var message = IOUtils.toString(in, UTF_8);
        if (message.isEmpty()) {
            // empty message
            return null;
        }
        var firstChar = message.charAt(0);
        DBSInvalidations invalidations;
        if (firstChar == ID_JSON) {
            // Two kind of invalidations are possible: DBSInvalidations and DBSInvalidationsList
            // The goal is to prepare the migration to DBSInvalidationsList (HXCS-6116)
            // but we need to keep the backward compatibility with the old code
            invalidations = deserializeJsonInvalidations(message);
        } else {
            invalidations = new DBSInvalidations();
            if (firstChar == ALL_IDS) {
                invalidations.setAll();
            } else if (firstChar != ID_SEP) {
                // invalid message
                return null;
            } else {
                // Split the message by the separator ','
                var ids = message.split(",");
                invalidations.addAll(Arrays.asList(ids));
            }
        }
        return invalidations;
    }

    private static DBSInvalidations deserializeJsonInvalidations(String json) {
        DBSInvalidations invalidations;
        try {
           invalidations = MAPPER.readValue(json, DBSInvalidations.class);
       } catch (UnrecognizedPropertyException _) {
            // The json message could be the serialization of a DBSInvalidationsList
            var list = MAPPER.readValue(json, DBSInvalidationsList.class);
            invalidations = new DBSInvalidations();
            if (!list.isEmpty()) {
                for (DBSInvalidations inv : list.getInvalidations()) {
                    if (inv.all) {
                        invalidations.setAll();
                    } else if (inv.ids != null && !invalidations.all) {
                        invalidations.addAll(inv.ids);
                    }
                    if (inv.nodeId != null && invalidations.getNodeId() == null) {
                        invalidations.setNodeId(inv.nodeId);
                    }
                    if (inv.timestamp != null && invalidations.getTimestamp() == null) {
                        invalidations.setTimestamp(inv.timestamp);
                    }
                }
            }

       }
        return invalidations;
    }

    public String getNodeId() {
        return nodeId;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public boolean isMergeableWith(DBSInvalidations other) {
        return Objects.equals(nodeId, other.nodeId) //
                && Objects.equals(timestamp, other.timestamp);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(this.getClass().getSimpleName() + '(');
        if (all) {
            sb.append("all=true");
        }
        if (ids != null) {
            sb.append("ids=");
            sb.append(ids);
        }
        sb.append(", nodeId=");
        sb.append(nodeId);
        sb.append(", timestamp=");
        sb.append(timestamp);
        sb.append(')');
        return sb.toString();
    }

}
