/*
 * Copyright 2018-2020 Cosgy Dev
 * Edit 2025 THOMZY
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.jagrosh.jmusicbot;

import com.jagrosh.jmusicbot.queue.FairQueue;
import com.jagrosh.jmusicbot.queue.Queueable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author John Grosh (john.a.grosh@gmail.com)
 */
public class FairQueueTest {
    @Test
    public void differentIdentifierSize() {
        FairQueue<Q> queue = new FairQueue<>();
        int size = 100;
        for (int i = 0; i < size; i++)
            queue.add(new Q(i), false);
        assertEquals(size, queue.size());
    }

    @Test
    public void sameIdentifierSize() {
        FairQueue<Q> queue = new FairQueue<>();
        int size = 100;
        for (int i = 0; i < size; i++)
            queue.add(new Q(0), false);
        assertEquals(size, queue.size());
    }

    private class Q implements Queueable {
        private final long identifier;

        private Q(long identifier) {
            this.identifier = identifier;
        }

        @Override
        public long getIdentifier() {
            return identifier;
        }
    }
}
