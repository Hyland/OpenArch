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
package org.nuxeo.ecm.core.api.security;

import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * Access control entry, assigning a permission to a principal (user or group).
 * <p>
 * Optionally, the assignment can be denied instead of being granted.
 */
public final class ACE implements Serializable, Cloneable {

    private static final long serialVersionUID = 1L;

    public enum Status {
        PENDING, EFFECTIVE, ARCHIVED
    }

    public static final String USER_PREFIX = "u:";

    public static final String GROUP_PREFIX = "g:";

    /**
     * Separator used to combine an external ID with its source system ID.
     * Format: {@code externalId_#_systemId}
     */
    public static final String EXTERNAL_SYSTEM_SEPARATOR = "_#_";

    /**
     * An ACE that blocks all permissions for everyone.
     *
     * @since 6.0
     */
    public static final ACE BLOCK = ACE.builder()
                                       .principal(SecurityConstants.EVERYONE)
                                       .permission(SecurityConstants.EVERYTHING)
                                       .isGranted(false)
                                       .build();

    private final String principal;

    private final String permission;

    private final boolean isGranted;

    private Instant begin;

    private Instant end;

    private String creator;

    protected static final Pattern ID_PATTERN = Pattern.compile("^(.+):([^:]+):([^:]+):([^:]*):([^:]*):([^:]*)$");

    /**
     * Create an ACE from an id.
     *
     * @since 7.4
     */
    public static ACE fromId(String aceId) {
        if (aceId == null) {
            return null;
        }

        // An ACE is composed of tokens separated with the ":" character.
        // The first 3 tokens are mandatory, the following 3 tokens are optional.
        // The ":" separator is still present even if the tokens are empty.
        // Example: jsmith:ReadWrite:true:::
        // The first token (principal id) is allowed to contain embedded ":" (to handle user/group prefix, typically).
        Matcher m = ID_PATTERN.matcher(aceId);
        if (!m.matches()) {
            throw new IllegalArgumentException(String.format("Invalid ACE id: %s", aceId));
        }

        String[] parts = new String[m.groupCount()];
        for (int i = 1; i <= m.groupCount(); i++) {
            parts[i - 1] = m.group(i);
        }

        String principal = parts[0];
        String permission = parts[1];
        boolean isGranted = Boolean.parseBoolean(parts[2]);

        ACEBuilder builder = ACE.builder().principal(principal).permission(permission).isGranted(isGranted);

        if (parts.length >= 4 && StringUtils.isNotBlank(parts[3])) {
            builder.creator(parts[3]);
        }

        if (parts.length >= 5 && StringUtils.isNotBlank(parts[4])) {
            Instant begin = Instant.ofEpochMilli(Long.parseLong(parts[4]));
            builder.begin(begin);
        }

        if (parts.length >= 6 && StringUtils.isNotBlank(parts[5])) {
            Instant end = Instant.ofEpochMilli(Long.parseLong(parts[5]));
            builder.end(end);
        }

        return builder.build();
    }

    /**
     * Constructs an ACE for a given principal, permission, specifying whether to grant or deny it, creator user, begin
     * and end date.
     *
     * @since 7.4
     */
    ACE(String principal, String permission, boolean isGranted, String creator, Instant begin, Instant end) {
        this.principal = Objects.requireNonNull(principal);
        this.permission = Objects.requireNonNull(permission);
        this.isGranted = isGranted;
        this.creator = creator;
        setBegin(begin);
        setEnd(end);
        if (begin != null && end != null && begin.isAfter(end)) {
            throw new IllegalArgumentException("'begin' date cannot be after 'end' date");
        }
    }

    public void checkPrincipal() {
        Objects.requireNonNull(principal);
        if (SecurityConstants.EVERYONE.equals(principal)) {
            return;
        }
        if (principal.startsWith(USER_PREFIX) || principal.startsWith(GROUP_PREFIX)) {
            return;
        }
        throw new IllegalArgumentException(
                "ACE principal '%s' should be prefixed by '%s' (user) or '%s' (group) in %s".formatted( //
                        principal, USER_PREFIX, GROUP_PREFIX, getId()));
    }

    /**
     * Returns this ACE id.
     * <p>
     * This id is unique inside a given ACL.
     *
     * @since 7.4
     */
    public String getId() {
        StringBuilder sb = new StringBuilder();
        sb.append(principal);
        sb.append(':');
        sb.append(permission);
        sb.append(':');
        sb.append(isGranted);

        sb.append(':');
        if (creator != null) {
            sb.append(creator);
        }

        sb.append(':');
        if (begin != null) {
            sb.append(begin.toEpochMilli());
        }

        sb.append(':');
        if (end != null) {
            sb.append(end.toEpochMilli());
        }

        return sb.toString();
    }

    public String getPrincipal() {
        return principal;
    }

    public String getPermission() {
        return permission;
    }

    /**
     * Checks if this privilege is granted.
     *
     * @return true if the privilege is granted
     */
    public boolean isGranted() {
        return isGranted;
    }

    /**
     * Checks if this privilege is denied.
     *
     * @return true if privilege is denied
     */
    public boolean isDenied() {
        return !isGranted;
    }

    public Instant getBegin() {
        return begin;
    }

    /**
     * Sets the begin date of this ACE.
     * <p>
     * Sets the millisecond part of the date to 0.
     */
    public void setBegin(Instant begin) {
        if (begin != null) {
            begin = begin.truncatedTo(ChronoUnit.SECONDS);
        }
        this.begin = begin;
    }

    public Instant getEnd() {
        return end;
    }

    /**
     * Sets the end date of this ACE.
     * <p>
     * Sets the millisecond part of the date to 0.
     */
    public void setEnd(Instant end) {
        if (end != null) {
            end = end.truncatedTo(ChronoUnit.SECONDS);
        }
        this.end = end;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    /**
     * Returns the status of this ACE.
     *
     * @since 7.4
     */
    public Status getStatus() {
        Status status = Status.EFFECTIVE;
        Instant now = Instant.now();
        if (begin != null && now.isBefore(begin)) {
            status = Status.PENDING;
        }
        if (end != null && now.isAfter(end)) {
            status = Status.ARCHIVED;
        }
        return status;
    }

    /**
     * Returns a Long value of this ACE status.
     * <p>
     * It returns {@code null} if there is no begin and end date, which means the ACE is effective. Otherwise, it
     * returns 0 for PENDING, 1 for EFFECTIVE and 2 for ARCHIVED.
     *
     * @since 7.4
     */
    public Long getLongStatus() {
        if (begin == null && end == null) {
            return null;
        }
        return Long.valueOf(getStatus().ordinal());
    }

    public boolean isEffective() {
        return getStatus() == Status.EFFECTIVE;
    }

    public boolean isPending() {
        return getStatus() == Status.PENDING;
    }

    public boolean isArchived() {
        return getStatus() == Status.ARCHIVED;
    }

    public boolean isBlockingInheritance() {
        return isEffective() //
                && isDenied() //
                && SecurityConstants.EVERYTHING.equals(permission) //
                && SecurityConstants.EVERYONE.equals(principal);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof ACE ace) {
            boolean beginEqual = Objects.equals(ace.begin, begin);
            boolean endEqual = Objects.equals(ace.end, end);
            boolean creatorEqual = Objects.equals(ace.creator, creator);
            boolean principalEqual = Objects.equals(ace.principal, principal);
            boolean permissionEqual = Objects.equals(ace.permission, permission);
            return ace.isGranted == isGranted && principalEqual && permissionEqual && creatorEqual && beginEqual
                    && endEqual;
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = hash * 37 + (isGranted ? 1 : 0);
        hash = principal != null ? hash * 37 + principal.hashCode() : hash;
        hash = creator != null ? hash * 37 + creator.hashCode() : hash;
        hash = begin != null ? hash * 37 + begin.hashCode() : hash;
        hash = end != null ? hash * 37 + end.hashCode() : hash;
        hash = permission != null ? hash * 37 + permission.hashCode() : hash;
        return hash;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append('(');
        sb.append("principal=").append(principal);
        sb.append(", ");
        sb.append("permission=").append(permission);
        sb.append(", ");
        sb.append("isGranted=").append(isGranted);
        sb.append(", ");
        sb.append("creator=").append(creator);
        sb.append(", ");
        sb.append("begin=").append(begin != null ? begin.toEpochMilli() : null);
        sb.append(", ");
        sb.append("end=").append(end != null ? end.toEpochMilli() : null);
        sb.append(')');
        return sb.toString();
    }

    @Override
    public ACE clone() {
        return new ACE(principal, permission, isGranted, creator, begin, end);
    }

    public static ACEBuilder builder() {
        return new ACEBuilder();
    }

    /** @since 11.3 */
    public static ACEBuilder builder(ACE ace) {
        return new ACEBuilder(ace);
    }

    public static class ACEBuilder {

        private String principal;

        private String permission;

        private boolean isGranted = true;

        private Instant begin;

        private Instant end;

        private String creator;

        public ACEBuilder() {
        }

        /** @since 11.3 */
        public ACEBuilder(ACE ace) {
            this.principal = ace.principal;
            this.permission = ace.permission;
            this.isGranted = ace.isGranted;
            this.begin = ace.begin;
            this.end = ace.end;
            this.creator = ace.creator;
        }

        public ACEBuilder(String principal, String permission) {
            this.principal = principal;
            this.permission = permission;
        }

        /** @since 11.3 */
        public ACEBuilder principal(String principal) {
            this.principal = principal;
            return this;
        }

        public ACEBuilder user(String user) {
            this.principal = USER_PREFIX + user;
            return this;
        }

        public ACEBuilder group(String group) {
            this.principal = GROUP_PREFIX + group;
            return this;
        }

        /** @since 11.3 */
        public ACEBuilder permission(String permission) {
            this.permission = permission;
            return this;
        }

        public ACEBuilder isGranted(boolean isGranted) {
            this.isGranted = isGranted;
            return this;
        }

        public ACEBuilder begin(Instant begin) {
            this.begin = begin;
            return this;
        }

        public ACEBuilder end(Instant end) {
            this.end = end;
            return this;
        }

        public ACEBuilder creator(String creator) {
            this.creator = creator;
            return this;
        }

        public ACE build() {
            return new ACE(principal, permission, isGranted, creator, begin, end);
        }
    }

}
