package com.mercateo.sqs.utils.message.handling;

import org.springframework.messaging.MessageHeaders;

public interface MessageWorkerWithHeaders<I, O> {

    O work(I object, MessageHeaders headers) throws Exception;

}
