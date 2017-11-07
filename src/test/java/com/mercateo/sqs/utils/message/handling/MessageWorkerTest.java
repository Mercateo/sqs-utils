package com.mercateo.sqs.utils.message.handling;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.springframework.messaging.MessageHeaders;

public class MessageWorkerTest {

    @Test
    public void testWorkDelegatesMethodCall() throws Exception {
        // given
        AtomicInteger counter = new AtomicInteger(0);
        MessageWorker<String, Integer> uut = new MessageWorker<String, Integer>() {
            @Override
            public Integer work(String object) {
                return counter.incrementAndGet();
            }
        };
        MessageHeaders messageHeaders = mock(MessageHeaders.class);

        // when
        uut.work("dummy value", messageHeaders);

        // then
        assertEquals(1, counter.intValue());
        verifyZeroInteractions(messageHeaders);
    }
}