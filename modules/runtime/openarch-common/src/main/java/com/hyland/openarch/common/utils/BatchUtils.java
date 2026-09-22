/*
 * (C) Copyright 2018-2026 Hyland (https://www.hyland.com/).
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
package com.hyland.openarch.common.utils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;

/**
 * Batching utilities.
 *
 * @since 10.10
 */
public class BatchUtils {

    private BatchUtils() {
        // utility class
    }

    /**
     * Takes the input {@code values} and batches them together in groups. All values in a given group have the same
     * derived value. The derived value is computed using the {@code deriver} function. Two derived values are compared
     * for equality using the {@code comparator}.
     *
     * @param <T> the type of the values
     * @param <U> the type of the derived values
     * @param values the input values
     * @param deriver the function to compute a derived value
     * @param comparator the equality test for derived values
     * @return a list of pairs with a derived values and the corresponding batch
     * @since 10.10
     */
    public static <T, U> List<Pair<U, List<T>>> groupByDerived(List<T> values, Function<T, U> deriver,
            BiPredicate<U, U> comparator) {
        List<Pair<U, List<T>>> result = new ArrayList<>();
        U previousDerived = null;
        List<T> batch = null;
        for (T value : values) {
            U derived = deriver.apply(value);
            if (batch == null || !comparator.test(derived, previousDerived)) {
                // start new batch
                batch = new ArrayList<>();
                result.add(Pair.of(derived, batch));
            }
            // add to current batch
            batch.add(value);
            previousDerived = derived;
        }
        return result;
    }

    /**
     * Tries to send the list to the consumer, but if the consumer throws a {@link TooLargeException} then retry with
     * the list split in two (recursively if needed).
     *
     * @throws TooLargeException if there's no way to split the list small enough to satisfy the consumer
     */
    public static <T> void tryWithSplit(List<T> list, Consumer<List<T>> consumer) {
        if (list.isEmpty()) {
            return;
        }
        TooLargeException exception = null;
        Deque<List<T>> todo = new ArrayDeque<>(List.of(list));
        while ((list = todo.pollFirst()) != null) {
            try {
                consumer.accept(list);
            } catch (TooLargeException e) {
                if (list.size() >= 2) {
                    // split in two and retry
                    int middle = list.size() / 2; // rounding down
                    todo.addFirst(list.subList(middle, list.size()));
                    todo.addFirst(list.subList(0, middle));
                } else {
                    // can't split further, a single task is already too big
                    TooLargeException ee = new TooLargeException("Task too large: " + list.iterator().next());
                    if (exception == null) {
                        exception = ee;
                    } else {
                        exception.addSuppressed(ee);
                    }
                }
            }
        }
        if (exception != null) {
            throw exception;
        }
    }

}
