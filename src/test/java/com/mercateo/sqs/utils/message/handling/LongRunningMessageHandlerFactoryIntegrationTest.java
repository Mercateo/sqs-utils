package com.mercateo.sqs.utils.message.handling;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import com.amazonaws.services.sqs.AmazonSQS;
import com.mercateo.sqs.utils.queue.Queue;
import com.mercateo.sqs.utils.queue.QueueFactory;
import com.mercateo.sqs.utils.queue.QueueName;
import com.mercateo.sqs.utils.visibility.VisibilityTimeoutExtenderFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.cloud.aws.messaging.listener.Acknowledgment;
import org.springframework.cloud.aws.messaging.listener.SimpleMessageListenerContainer;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;

@Slf4j
public class LongRunningMessageHandlerFactoryIntegrationTest {

    private final MessageHandlingRunnableFactory messageHandlingRunnableFactory = new MessageHandlingRunnableFactory();

    @Mock
    private QueueFactory queueFactory;

    private LongRunningMessageHandlerFactory longRunningMessageHandlerFactory;

    @Before
    public void setUp() {
        openMocks(this);
        VisibilityTimeoutExtenderFactory timeoutExtenderFactory = new VisibilityTimeoutExtenderFactory(mock(AmazonSQS.class));
        longRunningMessageHandlerFactory = new LongRunningMessageHandlerFactory(messageHandlingRunnableFactory,
                timeoutExtenderFactory, queueFactory, new SimpleMessageListenerContainer());
    }

    @Test
    public void graceful_shutdown_wait_for_tasks_to_complete_on_shutdown() {
        // given
        var longRunningMessageHandler = longRunningMessageHandlerFactory.get(1, worker(), queue(), finishedCallback(), Duration.ofMillis(1), true);
        var messages = IntStream.range(0, 300).mapToObj(this::message).collect(Collectors.toList());

        // when
        log.info("start " + messages.size() + " messages");
        messages.forEach(longRunningMessageHandler::handleMessage); // this is executed sequentially - cannot test concurrent behavior

        log.info("close longRunningMessageHandler");
        longRunningMessageHandler.close();

        // then
        log.info("finished");
        assertThat(messages)
                .describedAs(messages.toString())
                .extracting(msg -> msg.getPayload().getState())
                .containsOnly("finish");

    }

    @Test
    public void graceful_shutdown_do_not_wait_for_tasks_to_complete_on_shutdown() {
        // given
        var longRunningMessageHandler = longRunningMessageHandlerFactory.get(1, worker(), queue(), finishedCallback(), Duration.ofMillis(1), false);
        var messages = IntStream.range(0, 10_000).mapToObj(this::message).collect(Collectors.toList());

        // when
        log.info("start " + messages.size() + " messages");
        messages.forEach(longRunningMessageHandler::handleMessage); // this is executed sequentially - cannot test concurrent behavior

        log.info("close longRunningMessageHandler");
        longRunningMessageHandler.close();

        // then
        log.info("finished");
        assertThat(messages)
                .describedAs(messages.toString())
                .extracting(msg -> msg.getPayload().getState())
                .contains("work");

    }

    private Message<MessageObject> message(int number) {
        Map<String, Object> headers = new HashMap<>();
        headers.put("MessageId", "messageId" + number);
        headers.put("ReceiptHandle", "receiptHandle" + number);
        CompletableFuture<?> cf = new CompletableFuture<>();
        Acknowledgment acknowledgment = () -> cf;
        headers.put("Acknowledgment", acknowledgment);
        MessageHeaders messageHeaders = new MessageHeaders(headers);
        return new GenericMessage<>(new MessageObject(String.valueOf(number), Duration.ofMillis(number), cf), messageHeaders);
    }

    private MessageWorkerWithHeaders<MessageObject, MessageObject> worker() {
        return new MessageWorker<MessageObject, MessageObject>() {
            @Override
            public MessageObject work(MessageObject object) throws Exception {
                log.info("work " + object);
                Thread.sleep(object.executionDuration.toMillis());
                object.setState("work");
                return object;
            }
        };
    }

    private FinishedMessageCallback<MessageObject, MessageObject> finishedCallback() {
        return new FinishedMessageCallback<MessageObject, MessageObject>() {
            @Override
            public void call(MessageObject input, MessageObject output) {
                log.info("finish " + input);
                input.setState("finish");
                input.cf.complete(null);
            }
        };
    }


    @Data
    private static class MessageObject {
        private final String id;
        private final Duration executionDuration;
        private final CompletableFuture<?> cf;
        private String state;
    }

    private QueueName queue() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("VisibilityTimeout", "10");
        Queue queue = new Queue(new QueueName("queueName"), "queueUrl", attributes);
        when(queueFactory.get(any())).thenReturn(queue);
        return queue.getName();
    }

}
