package com.mercateo.sqs.utils.message.handling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.awspring.cloud.messaging.listener.Acknowledgment;

import java.util.HashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;

@SuppressWarnings("boxing")
class MessageHandlingRunnableTest {

    @Mock
    private MessageWorkerWithHeaders<Integer, String> worker;

    @Mock
    private Acknowledgment acknowledgment;

    private Message<Integer> message;

    @Mock
    private FinishedMessageCallback<Integer, String> finishedMessageCallback;

    @Mock
    private SetWithUpperBound<String> messages;

    @Mock
    private ScheduledFuture<?> visibilityTimeoutExtender;

    @Mock
    private ErrorHandlingStrategy<Integer> errorHandlingStrategy;

    private MessageHandlingRunnable<Integer, String> uut;

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        HashMap<String, Object> headerMap = new HashMap<>();
        headerMap.put("MessageId", "mid");
        headerMap.put("Acknowledgment", acknowledgment);
        message = new GenericMessage<>(3, new MessageHeaders(headerMap));
        uut = new MessageHandlingRunnable<>(worker, new MessageWrapper<>(message), finishedMessageCallback, messages,
                visibilityTimeoutExtender, errorHandlingStrategy);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testRun() throws Throwable {
        // given
        when(worker.work(3, message.getHeaders())).thenReturn("3S");
        when(acknowledgment.acknowledge()).thenReturn(mock(Future.class));

        // when
        uut.run();

        // then
        verify(finishedMessageCallback).call(3, "3S");
        verify(acknowledgment).acknowledge();
        verify(visibilityTimeoutExtender).cancel(false);
        verify(messages).remove("mid");
    }

    @Test
    void testRun_throws_workerException_and_does_not_ack() throws Throwable {
        // given
        Exception e = new IllegalArgumentException();
        doThrow(e).when(worker).work(3, message.getHeaders());
        doThrow(e).when(errorHandlingStrategy).handleWorkerException(e, message);

        // when
        Throwable result = catchThrowable(() -> uut.run());

        // then
        verifyNoInteractions(finishedMessageCallback);
        verifyNoInteractions(acknowledgment);
        assertThat(result).isEqualTo(e);
        verify(errorHandlingStrategy).handleWorkerException(e, message);
        verify(visibilityTimeoutExtender).cancel(false);
        verify(messages).remove("mid");
    }

    @SuppressWarnings("unchecked")
    @Test
    void testRun_throws_workerException_and_acks() throws Throwable {
        // given
        Exception e = new IllegalArgumentException();
        doThrow(e).when(worker).work(3, message.getHeaders());
        when(acknowledgment.acknowledge()).thenReturn(mock(Future.class));

        // when
        uut.run();

        // then
        verify(errorHandlingStrategy).handleWorkerException(e, message);
        verify(acknowledgment).acknowledge();
        verify(visibilityTimeoutExtender).cancel(false);
        verify(messages).remove("mid");
    }
}