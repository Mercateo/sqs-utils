package com.mercateo.sqs.utils.message.handling;

import lombok.extern.slf4j.Slf4j;

import org.springframework.messaging.Message;

@Slf4j
public class LogAndRethrowStrategy<I> implements ErrorHandlingStrategy<I> {

    @Override
    public void handle(Exception e, Message<I> message) {
        String messageId = message.getHeaders().get("MessageId", String.class);
        log.error("error while handling message " + messageId + ": " + message.getPayload(), e);
        throw new RuntimeException(e);
    }

}
