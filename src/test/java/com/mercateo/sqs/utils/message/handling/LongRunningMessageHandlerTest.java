package com.mercateo.sqs.utils.message.handling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;

import com.google.common.testing.NullPointerTester;
import com.google.common.testing.NullPointerTester.Visibility;
import com.mercateo.sqs.utils.queue.Queue;
import com.mercateo.sqs.utils.visibility.VisibilityTimeoutExtenderFactory;

public class LongRunningMessageHandlerTest {

    @Mock
    private ScheduledExecutorService timeoutExtensionExecutor;

    @Mock
    private MessageHandlingRunnableFactory messageHandlingRunnableFactory;

    @Mock
    private VisibilityTimeoutExtenderFactory timeoutExtenderFactory;

    @Mock
    private MessageWorkerWithHeaders<Integer, String> worker;

    @Mock
    private Queue queue;

    @Mock
    private FinishedMessageCallback<Integer, String> finishedMessageCallback;
    
    @Mock
    private ErrorHandlingStrategy<Integer> errorHandlingStrategy;

    private LongRunningMessageHandler<Integer, String> uut;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(queue.getDefaultVisibilityTimeout()).thenReturn(Duration.ofSeconds(120));
        uut = new LongRunningMessageHandler<>(timeoutExtensionExecutor, 1, 1,
                messageHandlingRunnableFactory, timeoutExtenderFactory, worker, queue,
                finishedMessageCallback, Duration.ofSeconds(115), Duration.ZERO, errorHandlingStrategy);
    }

    @Test
    public void testNullContracts() throws Exception {
        // given
        NullPointerTester nullPointerTester = new NullPointerTester();
        nullPointerTester.setDefault(VisibilityTimeoutExtenderFactory.class,
                timeoutExtenderFactory);
        nullPointerTester.setDefault(Queue.class, queue);

        // when
        nullPointerTester.testInstanceMethods(uut, NullPointerTester.Visibility.PACKAGE);
        nullPointerTester.testConstructors(uut.getClass(), Visibility.PACKAGE);
    }

    @Test(expected = IllegalStateException.class)
    public void timeUntilTimeOutExtensionTooLarge() throws Exception {
        // given

        // when
        uut = new LongRunningMessageHandler<>(timeoutExtensionExecutor, 10, 2,
                messageHandlingRunnableFactory, timeoutExtenderFactory, worker, queue,
                finishedMessageCallback, Duration.ofSeconds(116), Duration.ZERO, errorHandlingStrategy);

        // then
        // exception
    }

    @Test(expected = IllegalArgumentException.class)
    public void timeUntilTimeOutNegative() throws Exception {
        // given

        // when
        uut = new LongRunningMessageHandler<>(timeoutExtensionExecutor, 10, 2,
                messageHandlingRunnableFactory, timeoutExtenderFactory, worker, queue,
                finishedMessageCallback, Duration.ofSeconds(-5), Duration.ZERO, errorHandlingStrategy);

        // then
        // exception
    }

    @Test
    public void testHandleMessage_handlesExceptionDuringTimeoutExtension() {
        // given
        Message<Integer> message = createMessage();
        RuntimeException exception = new RuntimeException("test exception");
        when(timeoutExtensionExecutor.scheduleAtFixedRate(any(), anyLong(), anyLong(), any()))
                .thenThrow(exception);

        // when
        assertThatThrownBy(() -> uut.handleMessage(message)).hasCause(exception);

        // then
        assertThat(uut.getMessagesInProcessing().getBackingSet()).isEmpty();
    }

    private Message<Integer> createMessage() {
        Map<String, Object> headers = new HashMap<>();
        headers.put("MessageId", "messageId");

        MessageHeaders messageHeaders = new MessageHeaders(headers);
        return new GenericMessage<>(1, messageHeaders);
    }
}