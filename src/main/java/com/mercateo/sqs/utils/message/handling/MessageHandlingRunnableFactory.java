package com.mercateo.sqs.utils.message.handling;

import java.util.concurrent.ScheduledFuture;

import javax.inject.Named;

import org.springframework.messaging.Message;

import lombok.NonNull;

@Named
public class MessageHandlingRunnableFactory {

    <I, O> MessageHandlingRunnable<I, O> get(@NonNull MessageWorker<I, O> worker,
            @NonNull Message<I> message,
            @NonNull FinishedMessageCallback<I, O> finishedMessageCallback,
            @NonNull SetWithUpperBound<String> messageSet,
            @NonNull ScheduledFuture<?> visibilityTimeoutExtender) {

        return new MessageHandlingRunnable<>(worker, message, finishedMessageCallback, messageSet,
                visibilityTimeoutExtender);
    }
}