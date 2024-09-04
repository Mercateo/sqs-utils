package com.mercateo.sqs.utils.message.handling;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.testing.NullPointerTester;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;


public class SetWithUpperBoundTest {

    @Test
    void testNullContracts(){
        // given
        SetWithUpperBound<Integer> uut = new SetWithUpperBound<>(4);
        NullPointerTester nullPointerTester = new NullPointerTester();

        // when
        nullPointerTester.testInstanceMethods(uut, NullPointerTester.Visibility.PACKAGE);
        nullPointerTester.testAllPublicConstructors(uut.getClass());
    }

    @Test
    void testContains_contains() {
        // given
        SetWithUpperBound<String> setWithUpperBound = new SetWithUpperBound<>(2);
        setWithUpperBound.add("1");
        setWithUpperBound.add("2");

        // when
        boolean contains = setWithUpperBound.contains("2");

        // then
        assertThat(contains).isTrue();
    }

    @Test
    void testContains_doesNotContain() {
        // given
        SetWithUpperBound<String> setWithUpperBound = new SetWithUpperBound<>(2);
        setWithUpperBound.add("1");
        setWithUpperBound.add("2");

        // when
        boolean contains = setWithUpperBound.contains("3");

        // then
        assertThat(contains).isFalse();
    }

    @Test
    void testRemove() {
        // given
        SetWithUpperBound<String> setWithUpperBound = new SetWithUpperBound<>(2);
        setWithUpperBound.add("1");
        setWithUpperBound.add("2");

        // when
        setWithUpperBound.remove("2");

        // then
        assertThat(setWithUpperBound.contains("2")).isFalse();
    }

    @Test
    void testWaitUntilAtLeastOneFree_notifyAndWaitWorking() throws InterruptedException {
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
        assertThat(waitingThreads.await(100, TimeUnit.MILLISECONDS)).isTrue();
    }
}