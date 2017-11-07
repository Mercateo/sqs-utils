package com.mercateo.sqs.utils.message.handling;

import java.util.concurrent.ScheduledFuture;

import org.springframework.cloud.aws.messaging.listener.Acknowledgment;
import org.springframework.messaging.Message;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MessageHandlingRunnable<I, O> implements Runnable {

    private final MessageWorkerWithHeaders<I, O> worker;

    private final Message<I> message;

    private final FinishedMessageCallback<I, O> finishedMessageCallback;

    private final SetWithUpperBound<String> messages;

    private final ScheduledFuture<?> visibilityTimeoutExtender;

    MessageHandlingRunnable(@NonNull MessageWorkerWithHeaders<I, O> worker,
            @NonNull Message<I> message,
            @NonNull FinishedMessageCallback<I, O> finishedMessageCallback,
            @NonNull SetWithUpperBound<String> messages,
            @NonNull ScheduledFuture<?> visibilityTimeoutExtender) {

        this.worker = worker;
        this.message = message;
        this.finishedMessageCallback = finishedMessageCallback;
        this.messages = messages;
        this.visibilityTimeoutExtender = visibilityTimeoutExtender;
    }

    @Override
    public void run() {
        String messageId = message.getHeaders().get("MessageId", String.class);
        try {
            Acknowledgment acknowledgment = message.getHeaders().get("Acknowledgment",
                    Acknowledgment.class);

            log.info("starting processing of message " + messageId);
            O outcome = worker.work(message.getPayload(), message.getHeaders());

            finishedMessageCallback.call(message.getPayload(), outcome);

            acknowledgment.acknowledge().get();
            log.info("message task successfully processed and message acknowledged: " + messageId);
        } catch (Exception e) {
            log.error("error while handling message " + messageId + ": " + message.getPayload(), e);
            throw new RuntimeException(e);
        } finally {
            visibilityTimeoutExtender.cancel(false);
            messages.remove(messageId);
        }
    }
}