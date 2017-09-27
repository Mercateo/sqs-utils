package com.mercateo.sqs.utils.message.handling;

public interface FinishedMessageCallback<I, O> {

    void call(I input, O output);

}