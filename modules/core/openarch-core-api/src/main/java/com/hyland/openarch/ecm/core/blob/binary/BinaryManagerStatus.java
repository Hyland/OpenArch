/*
 * (C) Copyright 2011-2026 Hyland (https://www.hyland.com/).
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

import java.time.Duration;

/**
 * Status of a BinaryManager, including files that may have just been deleted by GC
 */
public class BinaryManagerStatus {

    private final long startTime = System.nanoTime();

    private Duration duration;

    private long numBinaries;

    private long sizeBinaries;

    private long numBinariesGC;

    private long sizeBinariesGC;

    /**
     * Adds to the number and cumulated size of the binaries (the values may be negative).
     */
    public void add(long num, long size) {
        numBinaries += num;
        sizeBinaries += size;
    }

    /**
     * Adds to the number and cumulated size of the garbage collected binaries.
     */
    public void addGC(long num, long size) {
        numBinariesGC += num;
        sizeBinariesGC += size;
    }

    /**
     * Adds the numbers and sizes of another status to this one.
     */
    public void add(BinaryManagerStatus status) {
        add(status.numBinaries, status.sizeBinaries);
        addGC(status.numBinariesGC, status.sizeBinariesGC);
    }

    /**
     * Finishes this status, recording the duration elapsed since its creation.
     */
    public void finish() {
        duration = Duration.ofNanos(System.nanoTime() - startTime);
    }

    /**
     * The GC duration, or {@code null} if not {@linkplain #finish() finished}.
     */
    public Duration getDuration() {
        return duration;
    }

    /**
     * The number of binaries.
     */
    public long getNumBinaries() {
        return numBinaries;
    }

    /**
     * The cumulated size of the binaries.
     */
    public long getSizeBinaries() {
        return sizeBinaries;
    }

    /**
     * The number of garbage collected binaries.
     */
    public long getNumBinariesGC() {
        return numBinariesGC;
    }

    /**
     * The cumulated size of the garbage collected binaries.
     */
    public long getSizeBinariesGC() {
        return sizeBinariesGC;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("BinaryManagerStatus[duration=").append(duration) //
        .append(", numBinaries=").append(numBinaries) //
        .append(", sizeBinaries=").append(sizeBinaries) //
        .append(", numBinariesGC=").append(numBinariesGC) //
        .append(", sizeBinariesGC=").append(sizeBinariesGC).append("]");
        return builder.toString();
    }

}
