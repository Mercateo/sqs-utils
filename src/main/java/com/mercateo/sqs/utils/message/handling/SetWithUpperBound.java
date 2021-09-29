/**
 * Copyright © 2017 Mercateo AG (http://www.mercateo.com)
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
package com.mercateo.sqs.utils.message.handling;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.NonNull;

class SetWithUpperBound<T> {

    private final Set<T> backingSet = ConcurrentHashMap.newKeySet();

    private final Object lock = new Object();

    private final int maximumSize;

    SetWithUpperBound(int maximumSize) {
        this.maximumSize = maximumSize;
    }

    boolean contains(@NonNull T object) {
        return backingSet.contains(object);
    }

    /**
     *
     * Tries to add a new element to the set
     *
     * @param object
     *            the element to be added
     */
    void add(@NonNull T object) {
        backingSet.add(object);
    }

    void remove(@NonNull T object) {
        backingSet.remove(object);
        interrupt();
    }

    void interrupt() {
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    void waitUntilAtLeastOneFree() {
        synchronized (lock) {
            while (!(backingSet.size() < maximumSize)) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Visible for Testing
     *
     * @return The contained concurrent set
     */
    Set<T> getBackingSet() {
        return backingSet;
    }
}