package com.mercateo.sqs.utils.message.handling;

import io.awspring.cloud.messaging.listener.Acknowledgment;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import org.springframework.messaging.Message;

@RequiredArgsConstructor
public class MessageWrapper<I> {

    @NonNull
    @Getter
    private final Message<I> message;

    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    @SneakyThrows
    public void acknowledge()  {
        getAcknowledgment().acknowledge().get(2, TimeUnit.MINUTES);
        acknowledged.set(true);
    }

    public boolean isAcknowledged() {
        return acknowledged.get();
    }

    public String getMessageId() {
        return message.getHeaders().get("MessageId", String.class);
    }

    public String getReceiptHandle() {
        return message.getHeaders().get("ReceiptHandle", String.class);
    }

    public Acknowledgment getAcknowledgment() {
        return message.getHeaders().get("Acknowledgment", Acknowledgment.class);
    }

}
