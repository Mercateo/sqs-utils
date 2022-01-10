package com.mercateo.sqs.utils.message.handling;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.testing.NullPointerTester;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;


public class SetWithUpperBoundTest {

    @Test
    public void testNullContracts() throws Exception {
        // given
        SetWithUpperBound<Integer> uut = new SetWithUpperBound<>(4);
        NullPointerTester nullPointerTester = new NullPointerTester();

        // when
        nullPointerTester.testInstanceMethods(uut, NullPointerTester.Visibility.PACKAGE);
        nullPointerTester.testAllPublicConstructors(uut.getClass());
    }

    @Test
    public void testContains_contains() {
        // given
        SetWithUpperBound<String> setWithUpperBound = new SetWithUpperBound<>(2);
        setWithUpperBound.add("1");
        setWithUpperBound.add("2");

        // when
        boolean contains = setWithUpperBound.contains("2");

        // then
        assertTrue(contains);
    }

    @Test
    public void testContains_doesNotContain() {
        // given
        SetWithUpperBound<String> setWithUpperBound = new SetWithUpperBound<>(2);
        setWithUpperBound.add("1");
        setWithUpperBound.add("2");

        // when
        boolean contains = setWithUpperBound.contains("3");

        // then
        assertFalse(contains);
    }

    @Test
    public void testRemove() {
        // given
        SetWithUpperBound<String> setWithUpperBound = new SetWithUpperBound<>(2);
        setWithUpperBound.add("1");
        setWithUpperBound.add("2");

        // when
        setWithUpperBound.remove("2");

        // then
        assertFalse(setWithUpperBound.contains("2"));
    }

    @Test
    public void testWaitUntilAtLeastOneFree_notifyAndWaitWorking() throws InterruptedException {
        // given
        CountDownLatch waitingThreads = new CountDownLatch(2);
        CountDownLatch waitingThreadsToBeStarted = new CountDownLatch(2);
        SetWithUpperBound<String> setWithUpperBound = new SetWithUpperBound<>(3);

        setWithUpperBound.add("hi");
        setWithUpperBound.add("hi2");
        setWithUpperBound.add("hi3");

        // when
        new Thread(() -> {
            waitingThreadsToBeStarted.countDown();
            setWithUpperBound.waitUntilAtLeastOneFree();
            waitingThreads.countDown();
        }).start();
        new Thread(() -> {
            waitingThreadsToBeStarted.countDown();
            setWithUpperBound.waitUntilAtLeastOneFree();
            waitingThreads.countDown();
        }).start();
        new Thread(() -> {
            try {
                waitingThreadsToBeStarted.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            setWithUpperBound.remove("hi");
        }).start();

        // then
        assertTrue(waitingThreads.await(100, TimeUnit.MILLISECONDS));
    }
}