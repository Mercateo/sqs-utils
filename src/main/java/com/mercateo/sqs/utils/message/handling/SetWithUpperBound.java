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