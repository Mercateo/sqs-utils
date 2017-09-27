package com.mercateo.sqs.utils.message.handling;

public interface MessageWorker<I, O> {

    O work(I object) throws Exception;

}
