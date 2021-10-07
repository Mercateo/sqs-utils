package com.mercateo.sqs.utils.message.handling;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cloud.aws.messaging.listener.Acknowledgment;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class LogAndRethrowStrategyTest {

    @Mock
    private Acknowledgment acknowledgment;

    private LogAndRethrowStrategy<Integer> uut;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        uut = new LogAndRethrowStrategy<>();
    }

    @Test
    public void testHandle() {
        // given
        Exception e = new IllegalArgumentException();
        Message<Integer> message = createMessage();

        // when
        Throwable throwable = catchThrowable(() -> uut.handle(e, message));

        // then
        assertThat(throwable).isInstanceOf(RuntimeException.class).hasCause(e);
    }

    private Message<Integer> createMessage() {
        HashMap<String, Object> headerMap = new HashMap<>();
        headerMap.put("MessageId", "mid");
        headerMap.put("Acknowledgment", acknowledgment);
        return new GenericMessage<>(3, new MessageHeaders(headerMap));
    }
}