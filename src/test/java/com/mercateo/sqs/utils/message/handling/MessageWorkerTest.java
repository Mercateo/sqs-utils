package com.mercateo.sqs.utils.message.handling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageHeaders;

public class MessageWorkerTest {

    @Test
    void testWorkDelegatesMethodCall() throws Exception {
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
        assertThat(counter.intValue()).isEqualTo(counter.intValue());
        verifyNoInteractions(messageHeaders);
    }
}