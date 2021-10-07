package com.mercateo.sqs.utils.message.handling;

import org.springframework.messaging.Message;

public interface ErrorHandlingStrategy<I> {

    void handle(Exception e, Message<I> message);

}
