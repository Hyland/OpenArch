/*
 * (C) Copyright 2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.blob.binary;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A slot of the blob key space, used to partition a blob garbage collection into independent, smaller runs.
 * <p>
 * Slots are based on the first characters of the (bare) blob key, which for content-addressed storage is a lowercase
 * hexadecimal digest. For a prefix length {@code L > 0} the key space is divided into {@code 16^L} hexadecimal slots
 * ({@code 0}..{@code f} for {@code L = 1}, {@code 00}..{@code ff} for {@code L = 2}) plus one "other" slot, whose id
 * is {@code L} times the letter {@code Z} ({@code Z}, {@code ZZ}), holding all the keys that don't start with
 * {@code L} lowercase hexadecimal characters (shorter keys, managed keys, temporary keys, etc.).
 * <p>
 * The slot {@link #ALL} (empty id) covers the whole key space, which means no partitioning.
 * <p>
 * The slot is computed from the bare blob key, without blob provider prefix and without version suffix.
 */
public record BlobKeySlot(String id) {

    // initialized first as it's used by the constructor of the constants below
    // keys are lowercase, an uppercase hexadecimal digit makes the key belong to the "other" slot
    private static final Pattern HEX_PATTERN = Pattern.compile("[0-9a-f]*");

    /** The slot covering the whole key space. */
    public static final BlobKeySlot ALL = new BlobKeySlot("");

    /** The character used (repeated) for the id of the "other" slot. */
    public static final char OTHER_CHAR = 'Z';

    /** The maximum supported prefix length, to avoid generating an absurd number of slots. */
    public static final int MAX_PREFIX_LENGTH = 2;

    private static final HexFormat HEX_FORMAT = HexFormat.of();

    public BlobKeySlot {
        Objects.requireNonNull(id, "id");
        if (id.length() > MAX_PREFIX_LENGTH) {
            throw new IllegalArgumentException("Invalid blob key slot (too long): " + id);
        }
        if (!isHexPrefix(id, id.length()) && !isOther(id)) {
            throw new IllegalArgumentException("Invalid blob key slot: " + id);
        }
    }

    /**
     * Returns the slots partitioning the key space for the given prefix length: just {@link #ALL} for {@code 0},
     * otherwise the hexadecimal slots in lexicographic order followed by the "other" slot.
     */
    public static List<BlobKeySlot> partition(int prefixLength) {
        if (prefixLength < 0 || prefixLength > MAX_PREFIX_LENGTH) {
            throw new IllegalArgumentException("Invalid blob key slot prefix length: " + prefixLength);
        }
        if (prefixLength == 0) {
            return List.of(ALL);
        }
        int count = 1 << (4 * prefixLength);
        List<BlobKeySlot> slots = new ArrayList<>(count + 1);
        for (int i = 0; i < count; i++) {
            slots.add(new BlobKeySlot(HEX_FORMAT.toHexDigits(i, prefixLength)));
        }
        slots.add(other(prefixLength));
        return slots;
    }

    /**
     * Returns the "other" slot for the given prefix length.
     */
    public static BlobKeySlot other(int prefixLength) {
        if (prefixLength <= 0) {
            throw new IllegalArgumentException("Invalid blob key slot prefix length: " + prefixLength);
        }
        return new BlobKeySlot(String.valueOf(OTHER_CHAR).repeat(prefixLength));
    }

    /**
     * Parses a slot id; an empty or {@code null} id means {@link #ALL}.
     */
    public static BlobKeySlot parse(String id) {
        return id == null || id.isEmpty() ? ALL : new BlobKeySlot(id);
    }

    public boolean isAll() {
        return id.isEmpty();
    }

    public boolean isOther() {
        return isOther(id);
    }

    /**
     * Gets the prefix shared by all the keys of this slot: empty for {@link #ALL}, the hexadecimal prefix for a
     * hexadecimal slot, or {@code null} for the "other" slot, whose keys share no common prefix.
     */
    public String getKeyPrefix() {
        return isOther() ? null : id;
    }

    /**
     * Checks whether the given bare blob key (no provider prefix, no version suffix) belongs to this slot.
     */
    public boolean contains(String key) {
        if (isOther()) {
            return !isHexPrefix(key, id.length());
        }
        return key.startsWith(id);
    }

    @Override
    public String toString() {
        return isAll() ? "ALL" : id;
    }

    private static boolean isOther(String id) {
        return !id.isEmpty() && id.chars().allMatch(c -> c == OTHER_CHAR);
    }

    // whether the string starts with length lowercase hexadecimal characters
    private static boolean isHexPrefix(String s, int length) {
        return s.length() >= length && HEX_PATTERN.matcher(s).region(0, length).matches();
    }

}
