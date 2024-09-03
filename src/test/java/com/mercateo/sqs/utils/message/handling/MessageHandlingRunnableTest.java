package com.mercateo.sqs.utils.message.handling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.testing.NullPointerTester;

import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;

@SuppressWarnings("boxing")
class MessageHandlingRunnableTest {

    @Mock
    private MessageWorkerWithHeaders<Integer, String> worker;

    @Mock
    private Acknowledgement acknowledgment;

    private MessageWrapper<Integer> message;

    @Mock
    private FinishedMessageCallback<Integer, String> finishedMessageCallback;

    @Mock
    private SetWithUpperBound<String> messages;

    private UUID messageGeneratedUUID;

    @Mock
    private ScheduledFuture<?> visibilityTimeoutExtender;

    @Mock
    private ErrorHandlingStrategy<Integer> errorHandlingStrategy;

    private MessageHandlingRunnable<Integer, String> uut;

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        HashMap<String, Object> headerMap = new HashMap<>();
        headerMap.put("MessageId", "bf308aa2-bf48-49b8-a839-61611c710431");
        headerMap.put("Acknowledgment", acknowledgment);
        message = new MessageWrapper<>(new GenericMessage<>(3, new MessageHeaders(headerMap)));
        messageGeneratedUUID = message.getMessage().getHeaders().getId();
        uut = new MessageHandlingRunnable<>(worker, message, finishedMessageCallback, messages,
                visibilityTimeoutExtender, errorHandlingStrategy);
    }

    @Test
    void testNullContracts() {
        // given
        NullPointerTester nullPointerTester = new NullPointerTester();
        nullPointerTester.setDefault(MessageWrapper.class, message);
        nullPointerTester.setDefault(SetWithUpperBound.class, messages);

        // when
        nullPointerTester.testInstanceMethods(uut, NullPointerTester.Visibility.PACKAGE);
        nullPointerTester.testAllPublicConstructors(uut.getClass());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testRun() throws Throwable {
        // given
        when(worker.work(3, message.getMessage().getHeaders())).thenReturn("3S");
        when(acknowledgment.acknowledgeAsync()).thenReturn(mock(CompletableFuture.class));

        // when
        uut.run();

        // then
        verify(finishedMessageCallback).call(3, "3S");
        verify(acknowledgment).acknowledgeAsync();
        verify(visibilityTimeoutExtender).cancel(false);
        verify(messages).remove(messageGeneratedUUID.toString());
    }

    @Test
    void testRun_throws_workerException_and_does_not_ack() throws Throwable {
        // given
        Exception e = new IllegalArgumentException();
        doThrow(e).when(worker).work(3, message.getMessage().getHeaders());
        doThrow(e).when(errorHandlingStrategy).handleWorkerException(e, message);

        // when
        Throwable result = catchThrowable(() -> uut.run());

        // then
        verifyNoInteractions(finishedMessageCallback);
        verifyNoInteractions(acknowledgment);
        assertThat(result).isEqualTo(e);
        verify(errorHandlingStrategy).handleWorkerException(e, message);
        verify(visibilityTimeoutExtender).cancel(false);
        verify(messages).remove(messageGeneratedUUID.toString());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testRun_throws_workerException_and_acks() throws Throwable {
        // given
        Exception e = new IllegalArgumentException();
        doThrow(e).when(worker).work(3, message.getMessage().getHeaders());
        when(acknowledgment.acknowledgeAsync()).thenReturn(mock(CompletableFuture.class));

        // when
        uut.run();

        // then
        verify(errorHandlingStrategy).handleWorkerException(e, message);
        verify(acknowledgment).acknowledgeAsync();
        verify(visibilityTimeoutExtender).cancel(false);
        verify(messages).remove(messageGeneratedUUID.toString());
    }
}