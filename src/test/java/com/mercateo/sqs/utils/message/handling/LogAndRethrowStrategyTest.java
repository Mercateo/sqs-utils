package com.mercateo.sqs.utils.message.handling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cloud.aws.messaging.listener.Acknowledgment;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;

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
    public void handle_throws_exception() {
        // Given
        Exception e = new IllegalArgumentException();
        Message<Integer> message = createMessage();

        // When
        Throwable throwable = catchThrowable(() -> uut.handle(e, message));

        // Then
        assertThat(throwable).isInstanceOf(IllegalArgumentException.class);

    }

    private Message<Integer> createMessage() {
        HashMap<String, Object> headerMap = new HashMap<>();
        headerMap.put("MessageId", "mid");
        headerMap.put("Acknowledgment", acknowledgment);
        return new GenericMessage<>(3, new MessageHeaders(headerMap));
    }

}