package com.mercateo.sqs.utils.message.handling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.google.common.testing.NullPointerTester;
import com.google.common.testing.NullPointerTester.Visibility;
import com.mercateo.sqs.utils.queue.Queue;
import com.mercateo.sqs.utils.queue.QueueName;
import com.mercateo.sqs.utils.visibility.VisibilityTimeoutExtenderFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;

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

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        when(queue.getName()).thenReturn(new QueueName("queuename"));
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

    public void timeUntilTimeOutExtensionTooLarge() throws Exception {

        // when
        Throwable result = catchThrowable(() -> uut = new LongRunningMessageHandler<>(timeoutExtensionExecutor, 10, 2,
                messageHandlingRunnableFactory, timeoutExtenderFactory, worker, queue,
                finishedMessageCallback, Duration.ofSeconds(116), Duration.ZERO, errorHandlingStrategy));

        // then
        assertThat(result).isInstanceOf(IllegalStateException.class);
    }

    public void timeUntilTimeOutNegative() throws Exception {

        // when
        Throwable result = catchThrowable(() -> uut = new LongRunningMessageHandler<>(timeoutExtensionExecutor, 10, 2,
                messageHandlingRunnableFactory, timeoutExtenderFactory, worker, queue,
                finishedMessageCallback, Duration.ofSeconds(-5), Duration.ZERO, errorHandlingStrategy));

        // then
        assertThat(result).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testHandleMessage_handlesExceptionDuringTimeoutExtension() {
        // given
        MessageWrapper<Integer> message = createMessage();
        RuntimeException exception = new RuntimeException("test exception");
        when(timeoutExtensionExecutor.scheduleAtFixedRate(any(), anyLong(), anyLong(), any()))
                .thenThrow(exception);

        // when
        assertThatThrownBy(() -> uut.handleMessage(message)).hasCause(exception);

        // then
        assertThat(uut.getMessagesInProcessing().getBackingSet()).isEmpty();
    }

    private MessageWrapper<Integer> createMessage() {
        Map<String, Object> headers = new HashMap<>();
        String uuid = UUID.fromString("bf308aa2-bf48-49b8-a839-61611c710430").toString();
        headers.put("id", uuid);

        MessageHeaders messageHeaders = new MessageHeaders(headers);
        return new MessageWrapper<>(new GenericMessage<>(1, messageHeaders));
    }
}