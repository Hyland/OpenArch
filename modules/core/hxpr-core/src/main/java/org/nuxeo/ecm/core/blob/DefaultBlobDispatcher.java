/*
 * (C) Copyright 2015-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.blob;

import static org.nuxeo.ecm.core.api.DocumentConstants.SYSFILE_BLOB;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYSGOV_IS_RECORD;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_PATH;
import static org.nuxeo.ecm.core.api.DocumentConstants.SYS_REPOSITORY;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_DIGEST;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_ENCODING;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_LENGTH;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_MIME_TYPE;
import static org.nuxeo.ecm.core.schema.TypeConstants.KEY_BLOB_FILENAME;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentSecurityException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.ecm.core.model.InternalDocument;
import org.nuxeo.ecm.core.model.InternalDocument.BlobAccessor;
import org.nuxeo.ecm.core.model.InternalSession;
import org.nuxeo.runtime.api.Framework;

/**
 * Default blob dispatcher, that uses the repository name as the blob provider.
 * <p>
 * Alternatively, it can be configured through properties to dispatch to a blob provider based on document properties
 * instead of the repository name.
 * <p>
 * The property name is a list of comma-separated clauses, with each clause consisting of a property, an operator and a
 * value. The property can be a {@link InternalDocument} xpath, {@code sys_repository}, {@code sys_path}, or, to match the
 * current blob being dispatched, {@code blob_filename}, {@code blob_mimeType}, {@code blob_encoding}, {@code blob_digest},
 * {@code blob_length} or {@code blob_xpath}.
 * <p>
 * Comma-separated clauses are ANDed together. The special name {@code default} defines the default provider, and must
 * be present.
 * <p>
 * Available operators between property and value are =, !=, &lt;, &lt;= ,&gt;, &gt;=, ~ and ^.
 * <p>
 * The operators =, !=, &lt;, &lt;=, &gt; and &gt;= work as numeric operators if the property is numeric, otherwise as
 * string comparisons operators.
 * <p>
 * The operator ~ does glob matching using {@code ?} to match a single arbitrary character, and {@code *} to match any
 * number of characters (including none). The operator ^ does full regexp matching.
 * <p>
 * For example, to dispatch to the "first" provider if my_format is "video", to the "second" provider if the blob's MIME
 * type is "video/mp4", to the "third" provider if the blob is stored as a secondary attached file, to the "fourth"
 * provider if the blob's document is stored in under an
 * "images" folder and the document is in the default repository, and otherwise to the "other" provider:
 *
 * <pre>
 * {@code
 * <property name="my_format=video">first</property>
 * <property name="blob_mimeType=video/mp4">second</property>
 * <property name="blob_xpath~files/*&#47;file">third</property>
 * <property name="sys_repository=default,sys_path^.*&#47images&#47.*">fourth</property>
 * <property name="default">other</property>
 * }
 * </pre>
 * <p>
 * You can make use of a record blob provider by using:
 *
 * <pre>
 * {@code
 * <property name="records">records</property>
 * <property name="default">other</property>
 * }
 * </pre>
 *
 * @since 7.3
 */
public class DefaultBlobDispatcher implements BlobDispatcher {

    private static final Logger log = LogManager.getLogger(DefaultBlobDispatcher.class);

    protected static final String NAME_DEFAULT = "default";

    protected static final String NAME_RECORDS = "records";

    // name="records" is equivalent to the following clause:
    protected static final String RECORDS_CLAUSE = "sysgov_isRecord=true,blob_xpath=" + SYSFILE_BLOB;

    protected static final Pattern NAME_PATTERN = Pattern.compile("(.*?)(=|!=|<=|<|>=|>|~|\\^)(.*)");

    protected static final String BLOB_PREFIX = "blob_";

    protected static final String BLOB_XPATH = "xpath";

    protected enum Op {
        EQ, NEQ, LT, LTE, GT, GTE, GLOB, RE;
    }

    protected static class Clause {
        public final String xpath;

        public final Op op;

        public final Object value;

        public Clause(String xpath, Op op, Object value) {
            this.xpath = xpath;
            this.op = op;
            this.value = value;
        }
    }

    protected static class Rule {
        public final List<Clause> clauses;

        public final String providerId;

        public Rule(List<Clause> clauses, String providerId) {
            this.clauses = clauses;
            this.providerId = providerId;
        }
    }

    // default to true when initialize is not called (default instance)
    // public for tests
    public boolean useRepositoryName = true;

    protected List<Rule> rules;

    protected Set<String> rulesXPaths;

    protected Set<String> providerIds;

    protected List<String> repositoryNames;

    // public for tests
    public String defaultProviderId;

    @Override
    public void initialize(Map<String, String> properties) {
        providerIds = new HashSet<>();
        rulesXPaths = new HashSet<>();
        rules = new ArrayList<>();
        for (Entry<String, String> en : properties.entrySet()) {
            String clausesString = en.getKey();
            String providerId = en.getValue();
            providerIds.add(providerId);
            if (clausesString.equals(NAME_RECORDS)) {
                clausesString = RECORDS_CLAUSE;
            }
            if (clausesString.equals(NAME_DEFAULT)) {
                defaultProviderId = providerId;
            } else {
                List<Clause> clauses = Arrays.stream(clausesString.split(","))
                                             .map(this::getClause)
                                             .filter(Objects::nonNull)
                                             .toList();
                if (!clauses.isEmpty()) {
                    rules.add(new Rule(clauses, providerId));
                    clauses.forEach(clause -> rulesXPaths.add(clause.xpath));
                }
            }
        }
        useRepositoryName = providerIds.isEmpty();
        if (!useRepositoryName && defaultProviderId == null) {
            log.error("Invalid dispatcher configuration, missing default, configuration will be ignored");
            useRepositoryName = true;
        }
    }

    protected Clause getClause(String name) {
        Matcher m = NAME_PATTERN.matcher(name);
        if (m.matches()) {
            String xpath = m.group(1);
            String ops = m.group(2);
            Object value = m.group(3);
            Op op;
            switch (ops) {
            case "=":
                op = Op.EQ;
                break;
            case "!=":
                op = Op.NEQ;
                break;
            case "<":
                op = Op.LT;
                break;
            case "<=":
                op = Op.LTE;
                break;
            case ">":
                op = Op.GT;
                break;
            case ">=":
                op = Op.GTE;
                break;
            case "~":
                op = Op.GLOB;
                value = getPatternFromGlob((String) value);
                break;
            case "^":
                op = Op.RE;
                value = Pattern.compile((String) value);
                break;
            default:
                log.error("Invalid dispatcher configuration operator: {}", ops);
                return null;
            }
            return new Clause(xpath, op, value);
        } else {
            log.error("Invalid dispatcher configuration property name: {}", name);
            return null;
        }
    }

    protected Pattern getPatternFromGlob(String glob) {
        // this relies on the fact that Pattern.quote wraps everything between \Q and \E
        // so we "open" the quoting to insert the corresponding regex for * and ?
        String regex = Pattern.quote(glob).replace("?", "\\E.\\Q").replace("*", "\\E.*\\Q");
        return Pattern.compile(regex);
    }

    @Override
    public Collection<String> getBlobProviderIds() {
        if (useRepositoryName) {
            if (repositoryNames == null) {
                repositoryNames = Framework.getService(RepositoryManager.class).getRepositoryNames();
            }
            return repositoryNames;
        }
        return providerIds;
    }

    protected String getProviderId(InternalDocument doc, Blob blob, String blobXPath) {
        if (useRepositoryName) {
            return doc.getRepositoryName();
        }
        NEXT_RULE: //
        for (Rule rule : rules) {
            for (Clause clause : rule.clauses) {
                Object value;
                try {
                    value = getValue(doc, blob, blobXPath, clause);
                } catch (PropertyNotFoundException e) {
                    continue NEXT_RULE;
                }
                if (!match(value, clause)) {
                    continue NEXT_RULE;
                }
            }
            return rule.providerId;
        }
        return defaultProviderId;
    }

    protected Object getValue(InternalDocument doc, Blob blob, String blobXPath, Clause clause) {
        String xpath = clause.xpath;
        if (xpath.equals(SYS_REPOSITORY)) {
            return doc.getRepositoryName();
        }
        if (xpath.equals(SYS_PATH)) {
            return doc.getPath();
        }
        if (xpath.equals(SYSGOV_IS_RECORD)) {
            return doc.isRecord();
        }
        if (xpath.startsWith(BLOB_PREFIX)) {
            switch (xpath.substring(BLOB_PREFIX.length())) {
            case KEY_BLOB_FILENAME:
                return blob.getFilename();
            case KEY_BLOB_MIME_TYPE:
                return blob.getMimeType();
            case KEY_BLOB_ENCODING:
                return blob.getEncoding();
            case KEY_BLOB_DIGEST:
                return blob.getDigest();
            case KEY_BLOB_LENGTH:
                return blob.getLength();
            case BLOB_XPATH:
                return blobXPath;
            default:
                log.error("Invalid dispatcher configuration property name: {}", xpath);
                throw new PropertyNotFoundException(xpath);
            }
        }
        try {
            return doc.getValue(xpath);
        } catch (PropertyNotFoundException e) {
            return doc.getPropertyValue(xpath); // may still throw PropertyNotFoundException
        }
    }

    protected boolean match(Object value, Clause clause) {
        switch (clause.op) {
        case EQ:
            return compare(value, clause, true, cmp -> cmp == 0);
        case NEQ:
            return compare(value, clause, true, cmp -> cmp != 0);
        case LT:
            return compare(value, clause, false, cmp -> cmp < 0);
        case LTE:
            return compare(value, clause, false, cmp -> cmp <= 0);
        case GT:
            return compare(value, clause, false, cmp -> cmp > 0);
        case GTE:
            return compare(value, clause, false, cmp -> cmp >= 0);
        case GLOB:
        case RE:
            return ((Pattern) clause.value).matcher(String.valueOf(value)).matches();
        default:
            throw new AssertionError("notreached");
        }
    }

    protected boolean compare(Object a, Clause clause, boolean eqneq, IntPredicate predicate) {
        String b = (String) clause.value;
        int cmp;
        if (a == null) {
            if (eqneq) {
                // treat null as the string "null" (backward compat)
                cmp = "null".compareTo(b);
            } else {
                // for <, >, etc. try to treat null as 0
                try {
                    // try Long
                    cmp = Long.compare(0, Long.parseLong(b));
                } catch (NumberFormatException e) {
                    try {
                        // try Double
                        cmp = Double.compare(0, Double.parseDouble(b));
                    } catch (NumberFormatException e2) {
                        // else treat null as empty string
                        cmp = "".compareTo(b);
                    }
                }
            }
        } else {
            if (a instanceof Long aLong) {
                try {
                    cmp = aLong.compareTo(Long.valueOf(b));
                } catch (NumberFormatException e) {
                    if (!eqneq) {
                        return false; // no match
                    }
                    cmp = 1; // different
                }
            } else if (a instanceof Double aDouble) {
                try {
                    cmp = aDouble.compareTo(Double.valueOf(b));
                } catch (NumberFormatException e) {
                    if (!eqneq) {
                        return false; // no match
                    }
                    cmp = 1; // different
                }
            } else if (a instanceof Instant aInstant) {
                try {
                    cmp = aInstant.compareTo(Instant.parse(b));
                } catch (DateTimeParseException e) {
                    if (!eqneq) {
                        return false; // no match
                    }
                    cmp = 1; // different
                }
            } else {
                cmp = String.valueOf(a).compareTo(b);
            }
        }
        return predicate.test(cmp);
    }

    @Override
    public String getBlobProvider(String repositoryName) {
        if (useRepositoryName) {
            return repositoryName;
        }
        // useful for legacy blobs created without prefix before dispatch was configured
        return defaultProviderId;
    }

    @Override
    public BlobDispatch getBlobProvider(InternalDocument doc, Blob blob, String xpath) {
        if (useRepositoryName) {
            String providerId = doc.getRepositoryName();
            return new BlobDispatch(providerId, false);
        }
        String providerId = getProviderId(doc, blob, xpath);
        return new BlobDispatch(providerId, true);
    }

    @Override
    public void notifyChanges(InternalDocument doc, Set<String> xpaths) {
        if (useRepositoryName) {
            return;
        }
        for (String xpath : rulesXPaths) {
            if (xpaths.contains(xpath)) {
                doc.visitBlobs(accessor -> checkBlob(doc, accessor));
                return;
            }
        }
    }

    /**
     * Checks if the blob is stored in the expected blob provider to which it's supposed to be dispatched. If not,
     * store it in the correct one (and maybe remove it from the previous one if it makes sense).
     */
    protected void checkBlob(InternalDocument doc, BlobAccessor accessor) {
        Blob blob = accessor.getBlob();
        if (!(blob instanceof ManagedBlob)) {
            return;
        }
        String xpath = accessor.getXPath();
        // compare current provider with expected
        ManagedBlob managedBlob = (ManagedBlob) blob;
        String previousProviderId = managedBlob.getProviderId();
        String expectedProviderId = getProviderId(doc, blob, xpath);
        if (previousProviderId.equals(expectedProviderId)) {
            return;
        }
        // re-dispatch blob to new blob provider
        // this calls back into blobProvider.writeBlob for the expected blob provider
        accessor.setBlob(blob);
        // if old blob provider is in record mode, delete from it
        deleteBlobIfRecord(previousProviderId, doc, xpath);
    }

    @Override
    public void notifyMakeRecord(InternalDocument doc) {
        notifyChanges(doc, Collections.singleton(SYSGOV_IS_RECORD));
    }

    @Override
    public void notifyAfterCopy(InternalDocument doc) {
        notifyChanges(doc, Collections.singleton(SYSGOV_IS_RECORD));
    }

    // TODO move this to caller

    @Override
    public void notifyBeforeRemove(InternalDocument doc) {
        String xpath = SYSFILE_BLOB;
        Blob blob;
        try {
            blob = (Blob) doc.getValue(xpath);
        } catch (PropertyNotFoundException e) {
            return;
        }
        if (!(blob instanceof ManagedBlob)) {
            return;
        }
        String blobProviderId = ((ManagedBlob) blob).getProviderId();
        deleteBlobIfRecord(blobProviderId, doc, xpath);
    }

    protected void deleteBlobIfRecord(String blobProviderId, InternalDocument doc, String xpath) {
        BlobProvider blobProvider = Framework.getService(BlobService.class).getBlobProvider(blobProviderId);
        if (blobProvider != null && blobProvider.isRecordMode()) {
            checkBlobCanBeDeleted(doc, xpath);
            blobProvider.deleteBlob(new BlobContext(doc, xpath));
        }
    }

    protected void checkBlobCanBeDeleted(InternalDocument doc, String xpath) {
        if (SYSFILE_BLOB.equals(xpath) && doc.isUnderRetentionOrLegalHold()) {
            if (!InternalSession.canDeleteUndeletable(NuxeoPrincipal.getCurrent())) {
                throw new DocumentSecurityException(
                        "Cannot remove main blob FROM SysContent " + doc.getId() + ", it is under retention / hold");
            }
        }
    }

}
