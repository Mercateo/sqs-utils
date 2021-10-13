package com.mercateo.sqs.utils.message.handling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Awaitility.to;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.amazonaws.services.sqs.AmazonSQS;
import com.mercateo.sqs.utils.queue.Queue;
import com.mercateo.sqs.utils.queue.QueueName;
import com.mercateo.sqs.utils.visibility.VisibilityTimeoutExtenderFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import lombok.Getter;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;

public class LongRunningMessageHandlerIntegrationTest {

    private MessageHandlingRunnableFactory messageHandlingRunnableFactory = new MessageHandlingRunnableFactory();

    @Mock
    private AmazonSQS sqsClient;

    private MessageWorkerWithHeaders<InputObject, String> worker = new TestWorkerWithHeaders();

    @Mock
    private FinishedMessageCallback<InputObject, String> finishedMessageCallback;

    @Spy
    private ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
    
    @Mock
    private ErrorHandlingStrategy<InputObject> errorHandlingStrategy;

    private LongRunningMessageHandler<InputObject, String> uut;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        Map<String, String> attributes = new HashMap<>();
        attributes.put("VisibilityTimeout", "10");
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
        Message<InputObject> message = createMessage(1);

        // when
        Thread thread = new Thread(() -> uut.handleMessage(message));
        thread.start();

        // then
        await().until(() -> !thread.isAlive());
        await().until(() -> message.getPayload().isRunning());
        assertThat(uut.getMessagesInProcessing().getBackingSet()).containsOnly("messageId1");
    }

    @Test
    public void testHandleMessage_processesTwoMessagesAndBlocks() {
        // given
        Message<InputObject> message1 = createMessage(1);
        Message<InputObject> message2 = createMessage(2);

        Thread thread1 = new Thread(() -> uut.handleMessage(message1));
        thread1.start();
        await().until(() -> !thread1.isAlive());

        // when
        Thread thread2 = new Thread(() -> uut.handleMessage(message2));
        thread2.start();

        // then
        await().until(() -> Thread.State.WAITING == thread2.getState());
        await().until(() -> message1.getPayload().isRunning());
        await().until(() -> message2.getPayload().isRunning());
        assertThat(uut.getMessagesInProcessing().getBackingSet()).containsOnly("messageId1",
                "messageId2");
    }

    @Test
    public void testHandleMessage_processesFourMessagesAndFillsQueue() {
        // given
        Message<InputObject> message1 = createMessage(1);
        Message<InputObject> message2 = createMessage(2);
        Message<InputObject> message3 = createMessage(3);
        Message<InputObject> message4 = createMessage(4);

        new Thread(() -> uut.handleMessage(message1)).start();
        new Thread(() -> uut.handleMessage(message2)).start();
        await().until(() -> message1.getPayload().isRunning());
        await().until(() -> message2.getPayload().isRunning());

        // when
        Thread thread3 = new Thread(() -> uut.handleMessage(message3));
        thread3.start();
        Thread thread4 = new Thread(() -> uut.handleMessage(message4));
        thread4.start();

        // then
        await().until(() -> Thread.State.WAITING == thread3.getState());
        await().until(() -> Thread.State.WAITING == thread4.getState());
        assertFalse(message3.getPayload().isRunning());
        assertFalse(message4.getPayload().isRunning());
        assertThat(uut.getMessagesInProcessing().getBackingSet()).containsOnly("messageId1",
                "messageId2", "messageId3", "messageId4");
        verify(scheduledExecutorService, times(4)).scheduleAtFixedRate(any(), anyLong(), anyLong(),
                any());
    }

    @Test
    public void testHandleMessage_processesSixMessageAndCrashes() {
        // given
        Message<InputObject> message1 = createMessage(1);
        Message<InputObject> message2 = createMessage(2);
        Message<InputObject> message3 = createMessage(3);
        Message<InputObject> message4 = createMessage(4);
        Message<InputObject> message5 = createMessage(5);
        Message<InputObject> message6 = createMessage(6);

        new Thread(() -> uut.handleMessage(message1)).start();
        new Thread(() -> uut.handleMessage(message2)).start();
        await().until(() -> message1.getPayload().isRunning());
        await().until(() -> message2.getPayload().isRunning());

        Thread thread3 = new Thread(() -> uut.handleMessage(message3));
        thread3.start();
        Thread thread4 = new Thread(() -> uut.handleMessage(message4));
        thread4.start();
        Thread thread5 = new Thread(() -> uut.handleMessage(message5));
        thread5.start();

        await().until(() -> Thread.State.WAITING == thread3.getState());
        await().until(() -> Thread.State.WAITING == thread4.getState());
        await().until(() -> Thread.State.WAITING == thread5.getState());

        // when
        assertThatThrownBy(() -> uut.handleMessage(message6));

        // then
        assertThat(uut.getMessagesInProcessing().getBackingSet()).containsOnly("messageId1",
                "messageId2", "messageId3", "messageId4", "messageId5");
    }

    @Test
    public void testHandleMessage_performsDeduplication() {
        // given
        Message<InputObject> message1_1 = createMessage(1);
        Message<InputObject> message1_2 = createMessage(1);

        Thread thread1 = new Thread(() -> uut.handleMessage(message1_1));
        thread1.start();
        await().until(() -> !thread1.isAlive());

        // when
        Thread thread2 = new Thread(() -> uut.handleMessage(message1_2));
        thread2.start();

        // then
        await().until(() -> !thread2.isAlive());
        assertTrue(message1_1.getPayload().isRunning());
        assertFalse(message1_2.getPayload().isRunning());
        assertThat(uut.getMessagesInProcessing().getBackingSet()).containsOnly("messageId1");
        verify(scheduledExecutorService).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());
    }

    @Test
    public void testHandleMessage_startsQueuedProcess() {
        // given
        Message<InputObject> message1 = createMessage(1);
        Message<InputObject> message2 = createMessage(2);
        Message<InputObject> message3 = createMessage(3);

        new Thread(() -> uut.handleMessage(message1)).start();
        await().until(() -> message1.getPayload().isRunning());
        Thread thread2 = new Thread(() -> uut.handleMessage(message2));
        thread2.start();
        await().until(() -> message2.getPayload().isRunning());
        Thread thread3 = new Thread(() -> uut.handleMessage(message3));
        thread3.start();

        // when
        message2.getPayload().stop();

        // then
        await().until(() -> Thread.State.WAITING == thread2.getState());
        await().until(() -> Thread.State.WAITING == thread3.getState());
        assertTrue(message3.getPayload().isRunning());
        assertThat(uut.getMessagesInProcessing().getBackingSet()).containsOnly("messageId1",
                "messageId3");
    }

    @Test
    public void testHandleMessage_resumesWaitingThreads() {
        // given
        Message<InputObject> message1 = createMessage(1);
        Message<InputObject> message2 = createMessage(2);

        Thread thread1 = new Thread(() -> uut.handleMessage(message1));
        thread1.start();
        await().until(() -> !thread1.isAlive());

        Thread thread2 = new Thread(() -> uut.handleMessage(message2));
        thread2.start();
        await().until(() -> Thread.State.WAITING == thread2.getState());

        // when
        message1.getPayload().stop();

        // then
        await().until(() -> !thread2.isAlive());
        assertThat(uut.getMessagesInProcessing().getBackingSet()).containsOnly("messageId2");
    }

    private Message<InputObject> createMessage(int number) {
        Map<String, Object> headers = new HashMap<>();
        headers.put("MessageId", "messageId" + number);
        headers.put("ReceiptHandle", "receiptHandle" + number);

        MessageHeaders messageHeaders = new MessageHeaders(headers);
        return new GenericMessage<>(new InputObject(), messageHeaders);
    }

    private class TestWorkerWithHeaders implements MessageWorkerWithHeaders<InputObject, String> {

        @Override
        public String work(InputObject object, MessageHeaders messageHeaders) throws Exception {

            object.start();

            await().untilCall(to(object).isFinished(), equalTo(true));

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