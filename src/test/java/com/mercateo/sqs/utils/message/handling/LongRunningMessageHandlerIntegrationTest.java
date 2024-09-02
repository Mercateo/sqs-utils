package com.mercateo.sqs.utils.message.handling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mercateo.sqs.utils.queue.Queue;
import com.mercateo.sqs.utils.queue.QueueName;
import com.mercateo.sqs.utils.visibility.VisibilityTimeoutExtenderFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import lombok.Getter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

public class LongRunningMessageHandlerIntegrationTest {

    private final MessageHandlingRunnableFactory messageHandlingRunnableFactory = new MessageHandlingRunnableFactory();

    @Mock
    private SqsAsyncClient sqsClient;

    private final MessageWorkerWithHeaders<InputObject, String> worker = new TestWorkerWithHeaders();

    @Mock
    private FinishedMessageCallback<InputObject, String> finishedMessageCallback;

    @Spy
    private ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);

    @Spy
    private ErrorHandlingStrategy<InputObject> errorHandlingStrategy;

    private LongRunningMessageHandler<InputObject, String> uut;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        Map<QueueAttributeName, String> attributes = new HashMap<>();
        attributes.put(QueueAttributeName.VISIBILITY_TIMEOUT, "10");
        Queue queue = new Queue(new QueueName("queueName"), "queueUrl", attributes);
        VisibilityTimeoutExtenderFactory timeoutExtenderFactory = new VisibilityTimeoutExtenderFactory(
                sqsClient);

        uut = new LongRunningMessageHandler<>(scheduledExecutorService, 4, 2,
                messageHandlingRunnableFactory, timeoutExtenderFactory, worker, queue,
                finishedMessageCallback, Duration.ofMillis(1), Duration.ZERO, errorHandlingStrategy);
    }

    @Test
    public void testHandleMessage_processesOneMessageAndReturns() {
        // given
        MessageWrapper<InputObject> message = createMessage(1);
        String uuid = String.valueOf(message.getMessage().getHeaders().get("id", UUID.class));

        // when
        Thread thread = new Thread(() -> uut.handleMessage(message.getMessage()));
        thread.start();

        // then
        await().until(() -> !thread.isAlive());
        await().until(() -> message.getMessage().getPayload().isRunning());
        assertThat(uut.getMessagesInProcessing().getBackingSet()).containsOnly(uuid);
    }

    @Test
    public void testHandleMessage_processesTwoMessagesAndBlocks() {
        // given
        MessageWrapper<InputObject> message1 = createMessage(1);
        MessageWrapper<InputObject> message2 = createMessage(2);
        List<String> uuids = List.of(String.valueOf(message1.getMessage().getHeaders().get("id", UUID.class)),
                String.valueOf(message2.getMessage().getHeaders().get("id", UUID.class)));

        Thread thread1 = new Thread(() -> uut.handleMessage(message1.getMessage()));
        thread1.start();
        await().until(() -> !thread1.isAlive());

        // when
        Thread thread2 = new Thread(() -> uut.handleMessage(message2.getMessage()));
        thread2.start();

        // then
        await().until(() -> Thread.State.WAITING == thread2.getState());
        await().until(() -> message1.getMessage().getPayload().isRunning());
        await().until(() -> message2.getMessage().getPayload().isRunning());
        assertThat(uut.getMessagesInProcessing().getBackingSet()).containsOnly(uuids.toArray(String[]::new));
    }

    @Test
    public void testHandleMessage_processesFourMessagesAndFillsQueue() {
        // given
        MessageWrapper<InputObject> message1 = createMessage(1);
        MessageWrapper<InputObject> message2 = createMessage(2);
        MessageWrapper<InputObject> message3 = createMessage(3);
        MessageWrapper<InputObject> message4 = createMessage(4);
        List<String> uuids = List.of(String.valueOf(message1.getMessage().getHeaders().get("id", UUID.class)),
                String.valueOf(message2.getMessage().getHeaders().get("id", UUID.class)),
                String.valueOf(message3.getMessage().getHeaders().get("id", UUID.class)),
                String.valueOf(message4.getMessage().getHeaders().get("id", UUID.class)));

        new Thread(() -> uut.handleMessage(message1.getMessage())).start();
        new Thread(() -> uut.handleMessage(message2.getMessage())).start();
        await().until(() -> message1.getMessage().getPayload().isRunning());
        await().until(() -> message2.getMessage().getPayload().isRunning());

        // when
        Thread thread3 = new Thread(() -> uut.handleMessage(message3.getMessage()));
        thread3.start();
        Thread thread4 = new Thread(() -> uut.handleMessage(message4.getMessage()));
        thread4.start();

        // then
        await().until(() -> Thread.State.WAITING == thread3.getState());
        await().until(() -> Thread.State.WAITING == thread4.getState());
        assertFalse(message3.getMessage().getPayload().isRunning());
        assertFalse(message4.getMessage().getPayload().isRunning());
        assertThat(uut.getMessagesInProcessing().getBackingSet()).containsOnly(uuids.toArray(String[]::new));
        verify(scheduledExecutorService, times(4)).scheduleAtFixedRate(any(), anyLong(), anyLong(),
                any());
    }

    @Test
    public void testHandleMessage_processesSixMessageAndCrashes() {
        // given
        MessageWrapper<InputObject> message1 = createMessage(1);
        MessageWrapper<InputObject> message2 = createMessage(2);
        MessageWrapper<InputObject> message3 = createMessage(3);
        MessageWrapper<InputObject> message4 = createMessage(4);
        MessageWrapper<InputObject> message5 = createMessage(5);
        MessageWrapper<InputObject> message6 = createMessage(6);

        List<String> uuids = List.of(message1.getMessage().getHeaders().get("id", UUID.class).toString(),
                message2.getMessage().getHeaders().get("id", UUID.class).toString(),
                message3.getMessage().getHeaders().get("id", UUID.class).toString(),
                message4.getMessage().getHeaders().get("id", UUID.class).toString(),
                message5.getMessage().getHeaders().get("id", UUID.class).toString());

        new Thread(() -> uut.handleMessage(message1.getMessage())).start();
        new Thread(() -> uut.handleMessage(message2.getMessage())).start();
        await().until(() -> message1.getMessage().getPayload().isRunning());
        await().until(() -> message2.getMessage().getPayload().isRunning());

        Thread thread3 = new Thread(() -> uut.handleMessage(message3.getMessage()));
        thread3.start();
        Thread thread4 = new Thread(() -> uut.handleMessage(message4.getMessage()));
        thread4.start();
        Thread thread5 = new Thread(() -> uut.handleMessage(message5.getMessage()));
        thread5.start();

        await().until(() -> Thread.State.WAITING == thread3.getState());
        await().until(() -> Thread.State.WAITING == thread4.getState());
        await().until(() -> Thread.State.WAITING == thread5.getState());

        // when
        assertThatThrownBy(() -> uut.handleMessage(message6.getMessage()));

        // then
        assertThat(uut.getMessagesInProcessing().getBackingSet()).containsOnly(uuids.toArray(String[]::new));
    }

    @Test
    public void testHandleMessage_startsQueuedProcess() {
        // given
        MessageWrapper<InputObject> message1 = createMessage(1);
        MessageWrapper<InputObject> message2 = createMessage(2);
        MessageWrapper<InputObject> message3 = createMessage(3);
        List<String> uuids = List.of(String.valueOf(message1.getMessage().getHeaders().get("id", UUID.class)),
                String.valueOf(message3.getMessage().getHeaders().get("id", UUID.class)));

        new Thread(() -> uut.handleMessage(message1.getMessage())).start();
        await().until(() -> message1.getMessage().getPayload().isRunning());
        Thread thread2 = new Thread(() -> uut.handleMessage(message2.getMessage()));
        thread2.start();
        await().until(() -> message2.getMessage().getPayload().isRunning());
        Thread thread3 = new Thread(() -> uut.handleMessage(message3.getMessage()));
        thread3.start();

        // when
        message2.getMessage().getPayload().stop();

        // then
        await().until(() -> Thread.State.WAITING == thread2.getState());
        await().until(() -> Thread.State.WAITING == thread3.getState());
        assertTrue(message3.getMessage().getPayload().isRunning());
        assertThat(uut.getMessagesInProcessing().getBackingSet()).containsOnly(uuids.toArray(String[]::new));
    }

    @Test
    public void testHandleMessage_resumesWaitingThreads() {
        // given
        MessageWrapper<InputObject> message1 = createMessage(1);
        MessageWrapper<InputObject> message2 = createMessage(2);
        List<String> uuids = List.of(String.valueOf(message2.getMessage().getHeaders().get("id", UUID.class)));

        Thread thread1 = new Thread(() -> uut.handleMessage(message1.getMessage()));
        thread1.start();
        await().until(() -> !thread1.isAlive());

        Thread thread2 = new Thread(() -> uut.handleMessage(message2.getMessage()));
        thread2.start();
        await().until(() -> Thread.State.WAITING == thread2.getState());

        // when
        message1.getMessage().getPayload().stop();

        // then
        await().until(() -> !thread2.isAlive());
        assertThat(uut.getMessagesInProcessing().getBackingSet()).containsOnly(uuids.toArray(String[]::new));
    }

    private MessageWrapper<InputObject> createMessage(int number) {
        Map<String, Object> headers = new HashMap<>();
        headers.put("id", UUID.fromString("bf308aa2-bf48-49b8-a839-61611c71043" + number).toString());
        headers.put("ReceiptHandle", "receiptHandle" + number);

        MessageHeaders messageHeaders = new MessageHeaders(headers);
        return new MessageWrapper<>(new GenericMessage<>(new InputObject(), messageHeaders));
    }

    private class TestWorkerWithHeaders implements MessageWorkerWithHeaders<InputObject, String> {

        @Override
        public String work(InputObject object, MessageHeaders messageHeaders) {

            object.start();
            await().until(object::isFinished);
            object.stop();

            return "done";
        }
    }

    @Getter
    private class InputObject {

        private boolean isRunning = false;

        private boolean isFinished = false;

        void start() {
            isRunning = true;
        }

        void stop() {
            isRunning = false;
            isFinished = true;
        }
    }
}