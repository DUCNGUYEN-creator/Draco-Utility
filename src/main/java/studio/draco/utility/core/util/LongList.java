/*
 * Draco Utility - client framework for Minecraft (Fabric)
 * Copyright (C) 2026 DUCNGUYEN-creator (Draco Studio)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package studio.draco.utility.core.util;

import java.util.Arrays;

/**
 * A growable list of primitive longs. Used for packed block positions so that scanning thousands of
 * blocks creates no garbage (no boxing, no BlockPos objects).
 */
public final class LongList {
    private long[] data;
    private int size;

    public LongList() {
        this(256);
    }

    public LongList(int capacity) {
        data = new long[Math.max(4, capacity)];
    }

    public void add(long value) {
        if (size == data.length) data = Arrays.copyOf(data, size * 2);
        data[size++] = value;
    }

    public long get(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException(index);
        return data[index];
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }

    /** Forgets the contents but keeps the storage for reuse. */
    public void clear() { size = 0; }

    /** An exact-size copy, safe to hand to another thread. */
    public long[] toArray() { return Arrays.copyOf(data, size); }
}
