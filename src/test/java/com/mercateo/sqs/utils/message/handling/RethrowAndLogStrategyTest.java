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

public class RethrowAndLogStrategyTest {

    @Mock
    private Acknowledgment acknowledgment;

    private RethrowAndLogStrategy<Integer> uut;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        uut = new RethrowAndLogStrategy<>();
    }

    @Test
        public void filterDLQExceptions_throws_exception() {
            // Given
            Exception e = new IllegalArgumentException();
            Message<Integer> message = createMessage();
    
            // When
            Throwable throwable = catchThrowable(() -> uut.filterDLQExceptions(e, message));
    
            // Then
            assertThat(throwable).isInstanceOf(RuntimeException.class).hasCause(e);
    
        }

    @Test
    public void handleDLQExceptions_does_not_throw_exception() {

        // Given
        Exception e = new IllegalArgumentException();
        Message<Integer> message = createMessage();

        // When
        Throwable throwable = catchThrowable(() -> uut.handleDLQExceptions(e, message));

        // Then
        assertThat(throwable).isNull();

    }

    private Message<Integer> createMessage() {
        HashMap<String, Object> headerMap = new HashMap<>();
        headerMap.put("MessageId", "mid");
        headerMap.put("Acknowledgment", acknowledgment);
        return new GenericMessage<>(3, new MessageHeaders(headerMap));
    }

}