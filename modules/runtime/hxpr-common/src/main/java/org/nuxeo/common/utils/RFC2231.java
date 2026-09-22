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

package org.nuxeo.common.utils;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.net.PercentCodec;

/**
 * RFC-2231 specifies how a MIME parameter value, like {@code Content-Disposition}'s {@code filename}, can be encoded to
 * contain arbitrary character sets.
 *
 * @author Florent Guillaume
 */
public class RFC2231 {

    // RFC 2045
    private static final String MIME_SPECIALS = "()<>@,;:\\\"/[]?=\t ";

    // RFC 2231
    private static final String RFC2231_SPECIALS = "*'%" + MIME_SPECIALS;

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    // Utility class
    private RFC2231() {
    }

    /**
     * Does a simple %-escaping of the UTF-8 bytes of the value. Keep only some know safe characters.
     *
     * @param sb the builder to which escaped chars are appended
     * @param value the value to escape
     */
    public static void percentEscape(StringBuilder sb, String value) {
        byte[] bytes = value.getBytes(UTF_8);
        for (byte b : bytes) {
            if (b < '+' || b == ';' || b == ',' || b == '\\' || b > 'z') {
                sb.append('%');
                String s = Integer.toHexString(b & 0xff).toUpperCase();
                if (s.length() < 2) {
                    sb.append('0');
                }
                sb.append(s);
            } else {
                sb.append((char) b);
            }
        }
    }

    public static String decodeContentDispositionFilename(String value) {
        if (!value.contains("%")) {
            return value;
        }
        byte[] bytes;
        try {
            bytes = new PercentCodec().decode(value.getBytes());
        } catch (DecoderException e) {
            throw new IllegalArgumentException("Invalid filename encoding: " + value);
        }
        return new String(bytes, UTF_8);
    }

    /**
     * Encodes a MIME parameter per RFC 2231.
     * <p>
     * This implementation always uses UTF-8 and no language.
     *
     * @param sb the buffer to fill
     * @param value the value to encode
     */
    protected static void encodeRFC2231(StringBuilder sb, String value) {
        int originalLength = sb.length();
        sb.append("*=UTF-8''"); // no language
        byte[] bytes = value.getBytes(UTF_8);
        boolean encoded = false;
        for (int i = 0; i < bytes.length; i++) {
            int c = bytes[i] & 0xff;
            if (c <= 32 || c >= 127 || RFC2231_SPECIALS.indexOf(c) != -1) {
                sb.append('%');
                sb.append(HEX_DIGITS[c >> 4]);
                sb.append(HEX_DIGITS[c & 0xf]);
                encoded = true;
            } else {
                sb.append((char) c);
            }
        }
        if (!encoded) {
            // undo and use basic format
            sb.setLength(originalLength);
            sb.append('=');
            sb.append(value);
        }
    }

    // used when no filename is available, so callers still get a well-formed Content-Disposition instead of an NPE
    private static final String DEFAULT_FILENAME = "file";

    /**
     * Encodes a {@code Content-Disposition} header. For some user agents the full RFC-2231 encoding won't be performed
     * as they don't understand it.
     *
     * @param filename the filename, or {@code null} if unknown
     * @param inline {@code true} for an inline disposition, {@code false} for an attachment
     * @return a full string to set as value of a {@code Content-Disposition} header
     */
    public static String encodeContentDisposition(String filename, boolean inline) {
        StringBuilder sb = new StringBuilder();
        sb.append(inline ? "inline" : "attachment");
        sb.append("; filename");
        encodeRFC2231(sb, filename == null ? DEFAULT_FILENAME : filename);
        return sb.toString();
    }

    /**
     * Obtain file name from {@code Content-Disposition} header.
     *
     * @param inputContentDisposition the Content-Disposition
     * @return the file name
     */
    public static String getFileNameFromContentDisposition(String inputContentDisposition) {
        String[] contentDisposition = inputContentDisposition.split(";");
        String filenameStr = "filename";
        String finalFileName = null;
        for (String part : contentDisposition) {
            if ((part.trim().startsWith(filenameStr))) {
                String[] name = part.split("=");
                if ((name[0].replaceAll("\\s+", "")).equals(filenameStr + "*")) {
                    finalFileName = decodeContentDispositionFilename(name[1]
                        .trim()
                        // temporary simplification for Character Set and language
                        .replaceAll("[A-Za-z0-9-]{0,20}'[A-Za-z0-9-]{0,20}'","")
                        // we remove starting and ending " for interoperability reasons, but it's not part of the spec.
                        .replace("\"", ""));
                } else {
                    finalFileName = decodeContentDispositionFilename(name[1]
                        .trim()
                        // we remove starting and ending " for interoperability reasons, but it's not part of the spec.
                        .replace("\"", ""));
                }
                return finalFileName;
            }
        }
        return finalFileName;
    }

}
