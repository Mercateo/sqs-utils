package com.mercateo.sqs.utils.message.handling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;

public class LogAndRethrowStrategyTest {

    @Mock
    private Acknowledgement acknowledgment;

    private DefaultErrorHandlingStrategy<Integer> uut;

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        uut = new DefaultErrorHandlingStrategy<>();
    }

    @Test
    void handle_throws_exception() {
        // Given
        Exception e = new IllegalArgumentException();
        MessageWrapper<Integer> message = createMessage();

        // When
        Throwable throwable = catchThrowable(() -> uut.handleWorkerException(e, message));

        // Then
        assertThat(throwable).isInstanceOf(IllegalArgumentException.class);
    }

    private MessageWrapper<Integer> createMessage() {
        HashMap<String, Object> headerMap = new HashMap<>();
        headerMap.put("id", "mid");
        headerMap.put("Acknowledgment", acknowledgment);
        return new MessageWrapper<>(new GenericMessage<>(3, new MessageHeaders(headerMap)));
    }

}