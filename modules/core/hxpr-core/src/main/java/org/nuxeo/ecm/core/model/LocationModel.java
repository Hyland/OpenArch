/*
 * (C) Copyright 2025-2026 Hyland (http://www.hyland.com/) and others.
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
package org.nuxeo.ecm.core.model;

import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_POSITION;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_SPREADSHEET;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_TEXT;
import static org.nuxeo.ecm.core.api.EmbeddingConstants.EMBEDDING_FIELD_LOCATION_TIMESTAMP;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.NuxeoException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public record LocationModel(TextLocation text, ImageLocation position, VideoLocation timestamp,
                            SpreadSheetLocation spreadsheet) {

    private final static Logger log = LogManager.getLogger(LocationModel.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public LocationModel(int page, int paragraph) {
        this(new TextLocation(page, paragraph), null, null, null);
    }

    public LocationModel(int left, int top, int right, int bottom) {
        this(null, new ImageLocation(left, top, right, bottom), null, null);
    }

    public LocationModel(double start, double end) {
        this(null, null, new VideoLocation(start, end), null);
    }

    public LocationModel(String sheet, int row, int column) {
        this(null, null, null, new SpreadSheetLocation(sheet, row, column));
    }

    public static LocationModel from(Object location) {
        if (location == null) {
            return null;
        }
        if (!(location instanceof Map<?, ?> locMap)) {
            log.error("Invalid location value: {}", location);
            return null;
        }

        TextLocation textLocation = parseTextLocationComponent(locMap);
        ImageLocation imageLocation = parseImageLocationComponent(locMap);
        VideoLocation videoLocation = parseVideoLocationComponent(locMap);
        SpreadSheetLocation spreadSheetLocation = parseSpreadSheetLocationComponent(locMap);

        // Return null if no location components were found
        if (textLocation == null && imageLocation == null && videoLocation == null && spreadSheetLocation == null) {
            return null;
        }

        return new LocationModel(textLocation, imageLocation, videoLocation, spreadSheetLocation);
    }

    public static LocationModel from(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }

        Map<String, Object> map;
        try {
            map = MAPPER.readValue(json, Map.class);
        } catch (JacksonException e) {
            throw new NuxeoException("Invalid location json string %s".formatted(json), e);
        }
        return from(map);
    }

    private static TextLocation parseTextLocationComponent(Map<?, ?> locMap) {
        Object sectionObj = locMap.get(EMBEDDING_FIELD_LOCATION_TEXT);
        if (sectionObj instanceof Map<?, ?> textMap) {
            Integer page = parseInteger(textMap.get("page"));
            Integer paragraph = parseInteger(textMap.get("paragraph"));

            // If neither page nor paragraph is present → no text location
            if (page == null && paragraph == null) {
                return null;
            }

            // Use 0 as default for missing values (or omit in serialization if using NON_DEFAULT/NON_NULL)
            int pageVal = (page != null) ? page : 0;
            int paraVal = (paragraph != null) ? paragraph : 0;

            return new TextLocation(pageVal, paraVal);
        }
        return null;
    }

    private static ImageLocation parseImageLocationComponent(Map<?, ?> locMap) {
        Object sectionObj = locMap.get(EMBEDDING_FIELD_LOCATION_POSITION);
        if (sectionObj instanceof Map<?, ?> posMap) {
            Integer left = parseInteger(posMap.get("left"));
            Integer top = parseInteger(posMap.get("top"));
            Integer right = parseInteger(posMap.get("right"));
            Integer bottom = parseInteger(posMap.get("bottom"));
            return (left != null && top != null && right != null && bottom != null)
                    ? new ImageLocation(left, top, right, bottom)
                    : null;
        }
        return null;
    }

    private static VideoLocation parseVideoLocationComponent(Map<?, ?> locMap) {
        Object sectionObj = locMap.get(EMBEDDING_FIELD_LOCATION_TIMESTAMP);
        if (sectionObj instanceof Map<?, ?> tsMap) {
            Double start = parseDouble(tsMap.get("start"));
            Double end = parseDouble(tsMap.get("end"));
            return (start != null && end != null) ? new VideoLocation(start, end) : null;
        }
        return null;
    }

    private static SpreadSheetLocation parseSpreadSheetLocationComponent(Map<?, ?> locMap) {
        Object sectionObj = locMap.get(EMBEDDING_FIELD_LOCATION_SPREADSHEET);
        if (sectionObj instanceof Map<?, ?> ssMap) {
            String sheet = parseString(ssMap.get("sheet"));
            Integer row = parseInteger(ssMap.get("row"));
            Integer column = parseInteger(ssMap.get("column"));
            return (sheet != null && row != null && column != null) ? new SpreadSheetLocation(sheet, row, column)
                    : null;
        }
        return null;
    }

    private static Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static Double parseDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string) {
            try {
                return Double.parseDouble(string);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String parseString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String string) {
            return string;
        }
        return String.valueOf(value);
    }

    public record TextLocation(int page, int paragraph) {
    }

    public record ImageLocation(int left, int top, int right, int bottom) {
    }

    public record VideoLocation(double start, double end) {
    }

    public record SpreadSheetLocation(String sheet, int row, int column) {
    }
}
