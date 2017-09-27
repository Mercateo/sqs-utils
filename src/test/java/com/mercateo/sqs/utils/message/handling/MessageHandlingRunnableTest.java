package com.mercateo.sqs.utils.message.handling;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cloud.aws.messaging.listener.Acknowledgment;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;

import com.google.common.testing.NullPointerTester;

@SuppressWarnings("boxing")
public class MessageHandlingRunnableTest {

    @Mock
    private MessageWorker<Integer, String> worker;

    @Mock
    private Acknowledgment acknowledgment;

    private Message<Integer> message;

    @Mock
    private FinishedMessageCallback<Integer, String> finishedMessageCallback;

    @Mock
    private SetWithUpperBound<String> messages;

    @Mock
    private ScheduledFuture<?> visibilityTimeoutExtender;

    private MessageHandlingRunnable<Integer, String> uut;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        HashMap<String, Object> headerMap = new HashMap<>();
        headerMap.put("MessageId", "mid");
        headerMap.put("Acknowledgment", acknowledgment);
        message = new GenericMessage<>(3, new MessageHeaders(headerMap));
        uut = new MessageHandlingRunnable<>(worker, message, finishedMessageCallback, messages,
                visibilityTimeoutExtender);
    }

    @Test
    public void testNullContracts() throws Exception {
        // given
        NullPointerTester nullPointerTester = new NullPointerTester();

        // when
        nullPointerTester.testInstanceMethods(uut, NullPointerTester.Visibility.PACKAGE);
        nullPointerTester.testAllPublicConstructors(uut.getClass());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRun() throws Throwable {
        // given
        when(worker.work(3)).thenReturn("3S");
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
    public void testRun_workerException() throws Throwable {
        // given
        Exception e = new IllegalArgumentException();
        doThrow(e).when(worker).work(3);

        // when
        assertThatThrownBy(uut::run).isInstanceOf(RuntimeException.class).hasCause(e);

        // then
        verifyZeroInteractions(finishedMessageCallback);
        verifyZeroInteractions(acknowledgment);
        verify(visibilityTimeoutExtender).cancel(false);
        verify(messages).remove("mid");
    }
}