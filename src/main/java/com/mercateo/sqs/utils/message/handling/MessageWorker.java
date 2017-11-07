package com.mercateo.sqs.utils.message.handling;

import org.springframework.messaging.MessageHeaders;

public interface MessageWorker<I, O> extends MessageWorkerWithHeaders<I, O> {

    @Override
    default O work(I object, MessageHeaders headers) throws Exception {
        return work(object);
    }

    O work(I object) throws Exception;

}
