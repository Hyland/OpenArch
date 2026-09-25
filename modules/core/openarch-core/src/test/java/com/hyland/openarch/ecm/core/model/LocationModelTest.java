/*
 * (C) Copyright 2025-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.ecm.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import com.hyland.openarch.ecm.core.api.RepositoryException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LocationModelTest {

    @Nested
    class FromMapTests {

        @Nested
        class TextLocationTests {
            @Test
            void shouldParseValidTextLocation() {
                Map<String, Object> locationMap = createTextLocationMap(1, 2);

                LocationModel location = LocationModel.from(locationMap);

                assertNotNull(location);
                assertNotNull(location.text());
                assertEquals(1, location.text().page());
                assertEquals(2, location.text().paragraph());
                assertNull(location.position());
                assertNull(location.timestamp());
                assertNull(location.spreadsheet());
            }

            @Test
            void shouldParseTextLocationWithStringNumbers() {
                Map<String, Object> locationMap = new HashMap<>();
                Map<String, Object> textMap = new HashMap<>();
                textMap.put("page", "1");
                textMap.put("paragraph", "2");
                locationMap.put("text", textMap);

                LocationModel location = LocationModel.from(locationMap);

                assertNotNull(location);
                assertNotNull(location.text());
                assertEquals(1, location.text().page());
                assertEquals(2, location.text().paragraph());
            }

            @Test
            void shouldRejectIfPageParagraphBothMissingInTextLocation() {
                Map<String, Object> locationMap = new HashMap<>();
                Map<String, Object> textMap = new HashMap<>();
                //page and para both missing
                textMap.put("page", null);
                textMap.put("paragraph", null);
                locationMap.put("text", textMap);
                assertNull(LocationModel.from(locationMap));
            }

            @Test
            void shouldParseTextLocationIfOnlyPagePresent() {
                Map<String, Object> locationMap = new HashMap<>();
                Map<String, Object> textMap = new HashMap<>();
                textMap.put("page", 1);
                //paragraph not present
                locationMap.put("text", textMap);
                LocationModel location = LocationModel.from(locationMap);
                assertNotNull(location);
                assertNotNull(location.text());
                assertEquals(1, location.text().page());
                assertEquals(0, location.text().paragraph());
            }

            @Test
            void shouldParseTextLocationIfOnlyParagraphPresent() {
                Map<String, Object> locationMap = new HashMap<>();
                Map<String, Object> textMap = new HashMap<>();
                //page not present
                textMap.put("paragraph", 1);
                locationMap.put("text", textMap);
                LocationModel location = LocationModel.from(locationMap);
                assertNotNull(location);
                assertNotNull(location.text());
                assertEquals(0, location.text().page());
                assertEquals(1, location.text().paragraph());
            }

            @Test
            void shouldHandleIntegerLimits() {
                // Test TextLocation with max/min integer values
                Map<String, Object> textMap = createTextLocationMap(Integer.MAX_VALUE, Integer.MIN_VALUE);
                LocationModel textLocation = LocationModel.from(textMap);

                assertNotNull(textLocation);
                assertEquals(Integer.MAX_VALUE, textLocation.text().page());
                assertEquals(Integer.MIN_VALUE, textLocation.text().paragraph());
            }
        }

        @Nested
        class ImageLocationTests {
            @Test
            void shouldParseValidImageLocation() {
                Map<String, Object> locationMap = createPositionLocationMap(10, 20, 100, 200);

                LocationModel location = LocationModel.from(locationMap);

                assertNotNull(location);
                assertNotNull(location.position());
                assertEquals(10, location.position().left());
                assertEquals(20, location.position().top());
                assertEquals(100, location.position().right());
                assertEquals(200, location.position().bottom());
                assertNull(location.text());
                assertNull(location.timestamp());
                assertNull(location.spreadsheet());
            }

            @Test
            void shouldParseImageLocationWithVariousNumberTypes() {
                Map<String, Object> locationMap = new HashMap<>();
                Map<String, Object> positionMap = new HashMap<>();
                positionMap.put("left", 10L);
                positionMap.put("top", 20.0);
                positionMap.put("right", 100.5f);
                positionMap.put("bottom", (short) 200);
                locationMap.put("position", positionMap);

                LocationModel location = LocationModel.from(locationMap);

                assertNotNull(location);
                assertNotNull(location.position());
                assertEquals(10, location.position().left());
                assertEquals(20, location.position().top());
                assertEquals(100, location.position().right());
                assertEquals(200, location.position().bottom());
            }

            @Test
            void shouldParseZeroValues() {
                Map<String, Object> locationMap = createPositionLocationMap(0, 0, 0, 0);

                LocationModel location = LocationModel.from(locationMap);

                assertNotNull(location);
                assertNotNull(location.position());
                assertEquals(0, location.position().left());
                assertEquals(0, location.position().top());
                assertEquals(0, location.position().right());
                assertEquals(0, location.position().bottom());
            }

            @Test
            void shouldRejectIncompleteImageLocation() {
                Map<String, Object> locationMap = new HashMap<>();
                Map<String, Object> positionMap = new HashMap<>();
                positionMap.put("left", 10);
                positionMap.put("top", 20);
                // Missing right and bottom
                locationMap.put("position", positionMap);

                assertNull(LocationModel.from(locationMap));
            }

            @Test
            void shouldHandleIntegerLimits() {
                // Test ImageLocation with max/min integer values
                Map<String, Object> posMap = createPositionLocationMap(Integer.MAX_VALUE, Integer.MIN_VALUE,
                        Integer.MAX_VALUE, Integer.MIN_VALUE);
                LocationModel imageLocation = LocationModel.from(posMap);

                assertNotNull(imageLocation);
                assertEquals(Integer.MAX_VALUE, imageLocation.position().left());
                assertEquals(Integer.MIN_VALUE, imageLocation.position().top());
                assertEquals(Integer.MAX_VALUE, imageLocation.position().right());
                assertEquals(Integer.MIN_VALUE, imageLocation.position().bottom());
            }
        }

        @Nested
        class VideoLocationTests {
            @Test
            void shouldParseValidVideoLocation() {
                Map<String, Object> locationMap = createTimestampLocationMap(1.5, 3.0);

                LocationModel location = LocationModel.from(locationMap);

                assertNotNull(location);
                assertNotNull(location.timestamp());
                assertEquals(1.5, location.timestamp().start());
                assertEquals(3.0, location.timestamp().end());
                assertNull(location.text());
                assertNull(location.position());
                assertNull(location.spreadsheet());
            }

            @Test
            void shouldParseNegativeValues() {
                Map<String, Object> locationMap = createTimestampLocationMap(-1.0, -0.5);

                LocationModel location = LocationModel.from(locationMap);

                assertNotNull(location);
                assertNotNull(location.timestamp());
                assertEquals(-1.0, location.timestamp().start());
                assertEquals(-0.5, location.timestamp().end());
            }

            @Test
            void shouldRejectIncompleteVideoLocation() {
                Map<String, Object> locationMap = new HashMap<>();
                Map<String, Object> timestampMap = new HashMap<>();
                timestampMap.put("start", 1.5);
                // Missing end
                locationMap.put("timestamp", timestampMap);

                assertNull(LocationModel.from(locationMap));
            }

            @Test
            void shouldHandleExtremeDoubleValues() {
                // Test with max/min double values
                Map<String, Object> locationMap = createTimestampLocationMap(Double.MAX_VALUE, Double.MIN_VALUE);
                LocationModel location = LocationModel.from(locationMap);

                assertNotNull(location);
                assertEquals(Double.MAX_VALUE, location.timestamp().start());
                assertEquals(Double.MIN_VALUE, location.timestamp().end());
            }

            @Test
            void shouldHandleInfinityValues() {
                // Test with positive/negative infinity
                Map<String, Object> locationMap = createTimestampLocationMap(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
                LocationModel location = LocationModel.from(locationMap);

                assertNotNull(location);
                assertEquals(Double.POSITIVE_INFINITY, location.timestamp().start());
                assertEquals(Double.NEGATIVE_INFINITY, location.timestamp().end());
            }

            @Test
            void shouldHandleNaNValues() {
                // Test with NaN values
                Map<String, Object> locationMap = new HashMap<>();
                Map<String, Object> timestampMap = new HashMap<>();
                timestampMap.put("start", Double.NaN);
                timestampMap.put("end", Double.NaN);
                locationMap.put("timestamp", timestampMap);

                LocationModel location = LocationModel.from(locationMap);
                assertNotNull(location);
                assertTrue(Double.isNaN(location.timestamp().start()));
                assertTrue(Double.isNaN(location.timestamp().end()));
            }
        }

        @Nested
        class SpreadsheetLocationTests {
            @Test
            void shouldParseValidSpreadsheetLocation() {
                Map<String, Object> locationMap = createSpreadsheetLocationMap("Sheet1", 5, 3);

                LocationModel location = LocationModel.from(locationMap);

                assertNotNull(location);
                assertNotNull(location.spreadsheet());
                assertEquals("Sheet1", location.spreadsheet().sheet());
                assertEquals(5, location.spreadsheet().row());
                assertEquals(3, location.spreadsheet().column());
                assertNull(location.text());
                assertNull(location.position());
                assertNull(location.timestamp());
            }

            @Test
            void shouldHandleNonStringSheetName() {
                Map<String, Object> locationMap = new HashMap<>();
                Map<String, Object> spreadsheetMap = new HashMap<>();
                spreadsheetMap.put("sheet", 123); // Number instead of string
                spreadsheetMap.put("row", 5);
                spreadsheetMap.put("column", 3);
                locationMap.put("spreadsheet", spreadsheetMap);

                LocationModel location = LocationModel.from(locationMap);

                assertNotNull(location);
                assertNotNull(location.spreadsheet());
                assertEquals("123", location.spreadsheet().sheet());
                assertEquals(5, location.spreadsheet().row());
                assertEquals(3, location.spreadsheet().column());
            }

            @Test
            void shouldRejectIncompleteSpreadsheetLocation() {
                Map<String, Object> locationMap = new HashMap<>();
                Map<String, Object> spreadsheetMap = new HashMap<>();
                spreadsheetMap.put("sheet", "Sheet1");
                spreadsheetMap.put("row", 5);
                // Missing column
                locationMap.put("spreadsheet", spreadsheetMap);

                assertNull(LocationModel.from(locationMap));
            }

            @Test
            void shouldHandleIntegerLimits() {
                // Test SpreadsheetLocation with max/min integer values
                Map<String, Object> ssMap = createSpreadsheetLocationMap("Sheet1", Integer.MAX_VALUE, Integer.MIN_VALUE);
                LocationModel ssLocation = LocationModel.from(ssMap);

                assertNotNull(ssLocation);
                assertEquals(Integer.MAX_VALUE, ssLocation.spreadsheet().row());
                assertEquals(Integer.MIN_VALUE, ssLocation.spreadsheet().column());
            }
        }

        @Nested
        class MultipleLocationTypesTests {
            @Test
            void shouldParseMultipleLocationTypes() {
                Map<String, Object> locationMap = new HashMap<>();
                locationMap.putAll(createTextLocationMap(1, 2));

                // Extract position map and add it to locationMap
                Map<String, Object> positionLocationMap = createPositionLocationMap(10, 20, 100, 200);
                locationMap.put("position", positionLocationMap.get("position"));

                LocationModel location = LocationModel.from(locationMap);

                assertNotNull(location);
                assertNotNull(location.text());
                assertEquals(1, location.text().page());
                assertEquals(2, location.text().paragraph());
                assertNotNull(location.position());
                assertEquals(10, location.position().left());
                assertEquals(20, location.position().top());
                assertEquals(100, location.position().right());
                assertEquals(200, location.position().bottom());
                assertNull(location.timestamp());
                assertNull(location.spreadsheet());
            }
        }

        @Nested
        class InvalidInputTests {
            @Test
            void shouldRejectNullInput() {
                assertNull(LocationModel.from((Object) null));
                assertNull(LocationModel.from((Map<String, Object>) null));
            }

            @Test
            void shouldRejectNonMapInput() {
                assertThrows(RepositoryException.class, () -> LocationModel.from("not a map"));
                assertNull(LocationModel.from(123));
                assertNull(LocationModel.from(new Object()));
            }

            @Test
            void shouldRejectEmptyLocationMap() {
                Map<String, Object> locationMap = new HashMap<>();
                assertNull(LocationModel.from(locationMap));
            }

            @Test
            void shouldRejectUnknownLocationKeys() {
                Map<String, Object> locationMap = new HashMap<>();
                locationMap.put("unknown", Map.of("key", "value"));
                assertNull(LocationModel.from(locationMap));
            }

            @Test
            void shouldRejectInvalidNumberFormats() {
                Map<String, Object> locationMap = new HashMap<>();
                Map<String, Object> textMap = new HashMap<>();
                textMap.put("page", "not a number");
                textMap.put("paragraph", "also not a number");
                locationMap.put("text", textMap);

                assertNull(LocationModel.from(locationMap));
            }
        }

        // Helper methods
        private Map<String, Object> createTextLocationMap(int page, int paragraph) {
            Map<String, Object> locationMap = new HashMap<>();
            Map<String, Object> textMap = new HashMap<>();
            textMap.put("page", page);
            textMap.put("paragraph", paragraph);
            locationMap.put("text", textMap);
            return locationMap;
        }

        private Map<String, Object> createPositionLocationMap(int left, int top, int right, int bottom) {
            Map<String, Object> locationMap = new HashMap<>();
            Map<String, Object> positionMap = new HashMap<>();
            positionMap.put("left", left);
            positionMap.put("top", top);
            positionMap.put("right", right);
            positionMap.put("bottom", bottom);
            locationMap.put("position", positionMap);
            return locationMap;
        }

        private Map<String, Object> createTimestampLocationMap(double start, double end) {
            Map<String, Object> locationMap = new HashMap<>();
            Map<String, Object> timestampMap = new HashMap<>();
            timestampMap.put("start", start);
            timestampMap.put("end", end);
            locationMap.put("timestamp", timestampMap);
            return locationMap;
        }

        private Map<String, Object> createSpreadsheetLocationMap(String sheet, int row, int column) {
            Map<String, Object> locationMap = new HashMap<>();
            Map<String, Object> spreadsheetMap = new HashMap<>();
            spreadsheetMap.put("sheet", sheet);
            spreadsheetMap.put("row", row);
            spreadsheetMap.put("column", column);
            locationMap.put("spreadsheet", spreadsheetMap);
            return locationMap;
        }
    }

    @Nested
    class FromJsonTests {
        @Test
        void shouldParseTextLocationFromJson() {
            String json = "{\"text\": {\"page\": 1, \"paragraph\": 2}}";

            LocationModel location = LocationModel.from(json);

            assertNotNull(location);
            assertNotNull(location.text());
            assertEquals(1, location.text().page());
            assertEquals(2, location.text().paragraph());
        }

        @Test
        void shouldParseImageLocationFromJson() {
            String json = "{\"position\": {\"left\": 10, \"top\": 20, \"right\": 100, \"bottom\": 200}}";

            LocationModel location = LocationModel.from(json);

            assertNotNull(location);
            assertNotNull(location.position());
            assertEquals(10, location.position().left());
            assertEquals(20, location.position().top());
            assertEquals(100, location.position().right());
            assertEquals(200, location.position().bottom());
        }

        @Test
        void shouldParseVideoLocationFromJson() {
            String json = "{\"timestamp\": {\"start\": 1.5, \"end\": 3.0}}";

            LocationModel location = LocationModel.from(json);

            assertNotNull(location);
            assertNotNull(location.timestamp());
            assertEquals(1.5, location.timestamp().start());
            assertEquals(3.0, location.timestamp().end());
        }

        @Test
        void shouldParseSpreadsheetLocationFromJson() {
            String json = "{\"spreadsheet\": {\"sheet\": \"Sheet1\", \"row\": 5, \"column\": 3}}";

            LocationModel location = LocationModel.from(json);

            assertNotNull(location);
            assertNotNull(location.spreadsheet());
            assertEquals("Sheet1", location.spreadsheet().sheet());
            assertEquals(5, location.spreadsheet().row());
            assertEquals(3, location.spreadsheet().column());
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t", "\n"})
        void shouldRejectBlankJsonStrings(String json) {
            assertNull(LocationModel.from(json));
        }

        @Test
        void shouldRejectInvalidJson() {
            assertThrows(RepositoryException.class, () -> LocationModel.from("{invalid json}"));
            assertThrows(RepositoryException.class, () -> LocationModel.from("not json at all"));
            assertThrows(RepositoryException.class, () -> LocationModel.from("{\"unclosed\": "));
        }

    }
}
