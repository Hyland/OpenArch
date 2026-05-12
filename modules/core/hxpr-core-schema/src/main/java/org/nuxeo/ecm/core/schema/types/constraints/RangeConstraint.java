/*
 * (C) Copyright 2022 Hyland (https://www.hyland.com/) and others.
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
 *     David Edwards
 */
package org.nuxeo.ecm.core.schema.types.constraints;

public interface RangeConstraint<T extends Number & Comparable<T>> {

    T getMin();

    T getMax();

    default boolean validRange() {
        if (getMin() == null || getMax() == null) {
            return true;
        }
        return getMin().compareTo(getMax()) <= 0 ;
    }
}
