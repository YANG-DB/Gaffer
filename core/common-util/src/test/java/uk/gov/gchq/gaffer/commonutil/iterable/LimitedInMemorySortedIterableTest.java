/*
 * Copyright 2017-2021 Crown Copyright
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
package uk.gov.gchq.gaffer.commonutil.iterable;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LimitedInMemorySortedIterableTest {

    @Test
    public void shouldLimitEntries() {
        final LimitedInMemorySortedIterable<Integer> list = new LimitedInMemorySortedIterable<Integer>(Comparator.naturalOrder(), 100);
        final List<Integer> expectedItems = new ArrayList<>();
        IntStream.rangeClosed(1, 100).forEach(expectedItems::add);

        for (int i = 200; 0 < i; i--) {
            list.add(i);
        }

        assertEquals(expectedItems, Lists.newArrayList(list));
    }

    @Test
    public void shouldLimitAndDeduplicateEntries() {
        final LimitedInMemorySortedIterable<Integer> list = new LimitedInMemorySortedIterable<Integer>(Comparator.naturalOrder(), 2, true);

        list.add(1);
        list.add(1);
        list.add(2);
        list.add(1);
        list.add(2);
        list.add(10);

        assertEquals(Arrays.asList(1, 2), Lists.newArrayList(list));
    }

    @Test
    public void shouldDeduplicateEntries() {
        final LimitedInMemorySortedIterable<Integer> list = new LimitedInMemorySortedIterable<Integer>(Comparator.naturalOrder(), 100, true);

        list.add(1);
        list.add(1);

        assertEquals(Collections.singletonList(1), Lists.newArrayList(list));
    }

    @Test
    public void shouldNotDeduplicateEntries() {
        final LimitedInMemorySortedIterable<Integer> list = new LimitedInMemorySortedIterable<Integer>(Comparator.naturalOrder(), 100, false);

        list.add(1);
        list.add(1);

        assertEquals(Arrays.asList(1, 1), Lists.newArrayList(list));
    }

    @Test
    public void shouldLimitAndNotDeduplicateEntries() {
        final LimitedInMemorySortedIterable<Integer> list = new LimitedInMemorySortedIterable<Integer>(Comparator.naturalOrder(), 4, false);

        list.add(1);
        list.add(2);
        list.add(1);
        list.add(2);
        list.add(10);

        assertEquals(Arrays.asList(1, 1, 2, 2), Lists.newArrayList(list));
    }

    @Test
    public void shouldAddAll() {
        final LimitedInMemorySortedIterable<Integer> itr = new LimitedInMemorySortedIterable<Integer>(Comparator
                .naturalOrder(), 100);

        // When/Then
        final List<Integer> evens = IntStream.iterate(0, i -> i + 2)
                .limit(10)
                .boxed()
                .collect(Collectors.toList());

        final boolean evensResult = itr.addAll(evens);

        assertThat(evens).hasSize(10);
        assertThat(evensResult).isTrue();
        assertThat(itr).hasSize(10);

        List<Integer> list = Lists.newArrayList(itr);
        assertThat(list.get(0)).isZero();
        assertThat(list.get(list.size() - 1)).isEqualTo(18);
        final List<Integer> odds = IntStream.iterate(1, i -> i + 2)
                .limit(10)
                .boxed()
                .collect(Collectors.toList());

        final boolean oddsResult = itr.addAll(odds);
        list = Lists.newArrayList(itr);
        assertThat(odds).hasSize(10);
        assertThat(oddsResult).isTrue();
        assertThat(list).hasSize(20);
        assertThat(list.get(0)).isZero();
        assertThat(list.get(itr.size() - 1)).isEqualTo(19);
    }

    @Test
    public void shouldLimitEntriesOnAddAll() {
        // Given
        final LimitedInMemorySortedIterable<Integer> itr = new LimitedInMemorySortedIterable<Integer>(Comparator
                .naturalOrder(), 10);

        // When/Then
        final List<Integer> evens = IntStream.iterate(0, i -> i + 2)
                .limit(100)
                .boxed()
                .collect(Collectors.toList());

        final boolean evensResult = itr.addAll(evens);
        List<Integer> list = Lists.newArrayList(itr);

        assertThat(evens).hasSize(100);
        assertThat(evensResult).isTrue();
        assertThat(list).hasSize(10);
        assertThat(list.get(0)).isZero();
        assertThat(list.get(itr.size() - 1)).isEqualTo(18);

        final List<Integer> odds = IntStream.iterate(1, i -> i + 2)
                .limit(100)
                .boxed()
                .collect(Collectors.toList());

        final boolean oddsResult = itr.addAll(odds);
        list = Lists.newArrayList(itr);
        assertThat(odds).hasSize(100);
        assertThat(oddsResult).isTrue();
        assertThat(list).hasSize(10);
        assertThat(list.get(0)).isZero();
        assertThat(list.get(itr.size() - 1)).isEqualTo(9);
    }

    @Test
    public void shouldSortLargeNumberOfItems() {
        // Given
        final int streamSize = 1000000;
        final int resultLimit = 10000;

        final IntStream stream = new Random()
                .ints(streamSize * 2) // generate a few extra in case there are duplicates
                .distinct()
                .limit(streamSize);

        final LimitedInMemorySortedIterable<Integer> list = new LimitedInMemorySortedIterable<Integer>(Comparator.naturalOrder(), resultLimit, false);

        // When
        stream.forEach(i -> list.add(Math.abs(i)));
        final List<Integer> sortedElements = Lists.newArrayList(list);
        sortedElements.sort(Comparator.naturalOrder());

        // Then
        final List<Integer> expected = Lists.newArrayList(list);
        assertEquals(expected, sortedElements);
    }
}
