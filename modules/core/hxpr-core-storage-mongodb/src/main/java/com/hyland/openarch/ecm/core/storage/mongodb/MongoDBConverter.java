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
package com.hyland.openarch.ecm.core.storage.mongodb;

import static java.lang.Boolean.FALSE;
import static com.hyland.openarch.ecm.core.api.DocumentConstants.SYS_ID;
import static com.hyland.openarch.ecm.core.storage.State.NOP;
import static com.hyland.openarch.ecm.core.storage.mongodb.MongoDBRepository.MONGODB_EACH;
import static com.hyland.openarch.ecm.core.storage.mongodb.MongoDBRepository.MONGODB_ID;
import static com.hyland.openarch.ecm.core.storage.mongodb.MongoDBRepository.MONGODB_INC;
import static com.hyland.openarch.ecm.core.storage.mongodb.MongoDBRepository.MONGODB_PULLALL;
import static com.hyland.openarch.ecm.core.storage.mongodb.MongoDBRepository.MONGODB_PUSH;
import static com.hyland.openarch.ecm.core.storage.mongodb.MongoDBRepository.MONGODB_SET;
import static com.hyland.openarch.ecm.core.storage.mongodb.MongoDBRepository.MONGODB_TYPE;
import static com.hyland.openarch.ecm.core.storage.mongodb.MongoDBRepository.MONGODB_UNSET;
import static com.hyland.openarch.ecm.core.storage.mongodb.MongoDBRepository.ONE;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.Decimal128;
import com.hyland.openarch.ecm.core.api.model.Delta;
import com.hyland.openarch.ecm.core.schema.SchemaManager;
import com.hyland.openarch.ecm.core.storage.State;
import com.hyland.openarch.ecm.core.storage.State.ListDiff;
import com.hyland.openarch.ecm.core.storage.State.StateDiff;
import com.hyland.openarch.runtime.mongodb.MongoDBOperators;

import com.mongodb.client.model.Filters;

/**
 * Converts between MongoDB types (bson) and DBS types (diff, state, list, Object).
 * <p>
 * The MongoDB native "_id" can optionally be translated into a custom id in memory (usually "sys_id"). Otherwise it is
 * stripped from returned results.
 *
 * @since 9.1
 */
public class MongoDBConverter {

    /** A shared converter doing no schema-dependent conversion. */
    public static final MongoDBConverter DEFAULT_CONVERTER = new MongoDBConverter();

    /** The schema manager, or {@code null} for a converter doing no schema-dependent conversion. */
    protected final SchemaManager schemaManager;

    /**
     * Constructor for a converter doing no schema-dependent conversion.
     *
     * @since 10.3
     */
    public MongoDBConverter() {
        this(null);
    }

    /**
     * Constructor for a converter that also knows to optionally translate the native MongoDB "_id" into a custom id.
     * <p>
     * When {@code idValuesKeys} are provided, the ids are stored as longs.
     *
     * @param schemaManager the schema manager, or {@code null} for no schema-dependent conversion
     * @param idValuesKeys the keys corresponding to values that are ids
     */
    public MongoDBConverter(SchemaManager schemaManager) {
        this.schemaManager = schemaManager;
    }

    /**
     * Constructs a list of MongoDB updates from the given {@link StateDiff}.
     * <p>
     * We need a list because some cases need two operations to avoid conflicts.
     */
    public ConditionsAndUpdates diffToBson(StateDiff diff) {
        UpdateBuilder updateBuilder = new UpdateBuilder();
        return updateBuilder.build(diff);
    }

    public void putToBson(Document doc, String key, Object value) {
        doc.put(keyToBson(key), valueToBson(key, value));
    }

    public String keyToBson(String key) {
        return SYS_ID.equals(key) ? MONGODB_ID : key;
    }

    public Object valueToBson(String key, Object value) {
        if (value instanceof State state) {
            return stateToBson(state);
        } else if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> values = (List<Object>) value;
            return listToBson(key, values);
        } else {
            return scalarToBson(key, value);
        }
    }

    public Document stateToBson(State state) {
        Document doc = new Document();
        for (Entry<String, Object> en : state.entrySet()) {
            Object value = en.getValue();
            if (value != null) {
                putToBson(doc, en.getKey(), value);
            }
        }
        return doc;
    }

    public <T> List<Object> listToBson(String key, Collection<T> values) {
        ArrayList<Object> objects = new ArrayList<>(values.size());
        for (T value : values) {
            objects.add(valueToBson(key, value));
        }
        return objects;
    }

    public Bson filterEq(String key, Object value) {
        return Filters.eq(keyToBson(key), valueToBson(key, value));
    }

    public <T> Bson filterIn(String key, Collection<T> values) {
        return Filters.in(keyToBson(key), listToBson(key, values));
    }

    /**
     * Adds each of the doc's predicates (map representing a set of {key: value}) to the filter. If several of them use
     * the same key, the format of the filter is adjusted to use an $and instead of simple {key: value} filtering.
     *
     * @param filter the filter, which must be mutable
     * @param doc the predicates to add to the filter
     */
    public void addToFilter(Document filter, Document doc) {
        doc.forEach((key, value) -> {
            if (filter.containsKey(key)) {
                // use $and when there are several instances of the same key
                // we don't use filter.computeIfAbsent as we have to recreate a mutable list anyway
                // because the received list may be immutable
                List<Object> ands = computeIfAbsentMutableList(filter, MongoDBOperators.AND);
                if (key.equals(MongoDBOperators.AND)) {
                    // add an $and to a previous $and
                    @SuppressWarnings("unchecked")
                    List<Object> values = (List<Object>) value;
                    ands.addAll(values);
                } else {
                    // create a new $and
                    ands.add(new Document(key, filter.remove(key)));
                    ands.add(new Document(key, value));
                }
            } else {
                filter.put(key, value);
            }
        });
    }

    private static List<Object> computeIfAbsentMutableList(Document doc, String key) {
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) doc.get(key);
        if (list == null) {
            list = new ArrayList<>();
        } else {
            list = new ArrayList<>(list);
        }
        doc.put(key, list);
        return list;
    }

    public Object getFromBson(Document doc, String bsonKey, String key) {
        return bsonToValue(key, doc.get(bsonKey));
    }

    public String bsonToKey(String key) {
        return MONGODB_ID.equals(key) ? SYS_ID : key;
    }

    public State bsonToState(Document doc) {
        if (doc == null) {
            return null;
        }
        State state = new State(doc.keySet().size());
        for (String bsonKey : doc.keySet()) {
            String key = bsonToKey(bsonKey);
            state.put(key, getFromBson(doc, bsonKey, key));
        }
        return state;
    }

    public Object bsonToValue(String key, Object value) {
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                return null;
            } else {
                List<Object> res = new ArrayList<>(list.size());
                for (Object v : list) {
                    res.add(bsonToValue("", v));
                }
                return res;
            }
        } else if (value instanceof Document document) {
            return bsonToState(document);
        } else {
            return bsonToScalar(key, value);
        }
    }

    public Object scalarToBson(String key, Object value) {
        if (value instanceof Instant instant) {
            return Date.from(instant); // XXX TODO check if MongoDB accepts Instant directly
        }
        if (FALSE.equals(value) && key != null && schemaManager != null && schemaManager.isFalseStoredAsNull(key)) {
            return null;
        }
        return value;
    }

    public Object bsonToScalar(String key, Object val) {
        if (val instanceof Date date) {
            return date.toInstant();
        }
        if (val instanceof Decimal128 d128) {
            return d128.bigDecimalValue();
        }
        if (val instanceof Integer integer){
            return Long.valueOf(integer);
        }
        return val;
    }

    public static class ConditionsAndUpdates {

        public Document conditions = new Document();

        public List<Document> updates = new ArrayList<>();
    }

    /**
     * Update list builder to prevent several updates of the same field.
     * <p>
     * This happens if two operations act on two fields where one is a prefix of the other.
     * <p>
     * Example: Cannot update 'mylist.0.string' and 'mylist' at the same time (error 16837)
     *
     * @since 5.9.5
     */
    public class UpdateBuilder {

        protected final Document set = new Document();

        protected final Document unset = new Document();

        protected final Document push = new Document();

        protected final Document pull = new Document();

        protected final Document inc = new Document();

        protected final ConditionsAndUpdates conditionsAndUpdates = new ConditionsAndUpdates();

        protected Document update;

        protected Set<String> prefixKeys;

        protected Set<String> keys;

        public ConditionsAndUpdates build(StateDiff diff) {
            processStateDiff(diff, null);
            newUpdate();
            for (Entry<String, Object> en : set.entrySet()) {
                update(MONGODB_SET, en.getKey(), en.getValue());
            }
            for (Entry<String, Object> en : unset.entrySet()) {
                update(MONGODB_UNSET, en.getKey(), en.getValue());
            }
            for (Entry<String, Object> en : push.entrySet()) {
                update(MONGODB_PUSH, en.getKey(), en.getValue());
            }
            for (Entry<String, Object> en : pull.entrySet()) {
                update(MONGODB_PULLALL, en.getKey(), en.getValue());
            }
            for (Entry<String, Object> en : inc.entrySet()) {
                update(MONGODB_INC, en.getKey(), en.getValue());
            }
            return conditionsAndUpdates;
        }

        protected void processStateDiff(StateDiff diff, String prefix) {
            String elemPrefix = prefix == null ? "" : prefix + '.';
            for (Entry<String, Object> en : diff.entrySet()) {
                String name = elemPrefix + en.getKey();
                Object value = en.getValue();
                if (value instanceof StateDiff stateDiff) {
                    processStateDiff(stateDiff, name);
                } else if (value instanceof ListDiff listDiff) {
                    processListDiff(listDiff, name);
                } else if (value instanceof Delta delta) {
                    processDelta(delta, name);
                } else {
                    // not a diff
                    processValue(name, value);
                }
            }
        }

        protected void processListDiff(ListDiff listDiff, String prefix) {
            String bsonPrefix = keyToBson(prefix);
            if (listDiff.diff != null) {
                String elemPrefix = bsonPrefix + '.';
                int i = 0;
                for (Object value : listDiff.diff) {
                    String name = elemPrefix + i;
                    if (value instanceof StateDiff stateDiff) {
                        processStateDiff(stateDiff, name);
                    } else if (value != NOP) {
                        // set value
                        set.put(name, valueToBson(prefix, value));
                    }
                    i++;
                }
                // in order to protect against concurrent deletions of the array
                // which would make an update on foo.0.bar create a sub-document
                // instead of addressing the array element foo.0,
                // we add a condition on the type of what we're updating
                conditionsAndUpdates.conditions.put(bsonPrefix, new Document(MONGODB_TYPE, "array"));
            }
            if (listDiff.rpush != null) {
                Object pushed;
                if (listDiff.rpush.size() == 1) {
                    // no need to use $each for one element
                    pushed = valueToBson(prefix, listDiff.rpush.get(0));
                } else {
                    pushed = new Document(MONGODB_EACH, listToBson(prefix, listDiff.rpush));
                }
                push.put(bsonPrefix, pushed);
            }
            if (listDiff.pull != null) {
                pull.put(bsonPrefix, valueToBson(prefix, listDiff.pull));
            }
        }

        protected void processDelta(Delta delta, String prefix) {
            // MongoDB can $inc a field that doesn't exist, it's treated as 0 BUT it doesn't work on null
            // so we ensure (in processValue) that we never store a null but remove the field instead
            Object incValue = valueToBson(prefix, delta.getDeltaValue());
            inc.put(prefix, incValue);
        }

        protected void processValue(String name, Object value) {
            if (value == null) {
                // for null values, beyond the space saving,
                // it's important to unset the field instead of setting the value to null
                // because $inc does not work on nulls but works on non-existent fields
                unset.put(keyToBson(name), ONE);
            } else {
                set.put(keyToBson(name), valueToBson(name, value));
            }
        }

        protected void newUpdate() {
            update = new Document();
            conditionsAndUpdates.updates.add(update);
            prefixKeys = new HashSet<>();
            keys = new HashSet<>();
        }

        protected void update(String op, String bsonKey, Object val) {
            checkForConflict(bsonKey);
            Document map = (Document) update.computeIfAbsent(op, k -> new Document());
            map.put(bsonKey, val);
        }

        /**
         * Checks if the key conflicts with one of the previous keys.
         * <p>
         * A conflict occurs if one key is equals to or is a prefix of the other.
         */
        protected void checkForConflict(String key) {
            List<String> pKeys = getPrefixKeys(key);
            if (conflictKeys(key, pKeys)) {
                newUpdate();
            }
            prefixKeys.addAll(pKeys);
            keys.add(key);
        }

        protected boolean conflictKeys(String key, List<String> subkeys) {
            if (prefixKeys.contains(key)) {
                return true;
            }
            for (String sk : subkeys) {
                if (keys.contains(sk)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * return a list of parents key
         * <p>
         * foo.0.bar -&gt; [foo, foo.0, foo.0.bar]
         */
        protected List<String> getPrefixKeys(String key) {
            List<String> ret = new ArrayList<>(10);
            int i = 0;
            while ((i = key.indexOf('.', i)) > 0) {
                ret.add(key.substring(0, i++));
            }
            ret.add(key);
            return ret;
        }

    }

}
