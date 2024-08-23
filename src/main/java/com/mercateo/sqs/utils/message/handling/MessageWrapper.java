package com.mercateo.sqs.utils.message.handling;

import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;

import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import org.springframework.messaging.Message;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;

@RequiredArgsConstructor
public class MessageWrapper<I> {

    @NonNull
    @Getter
    private final Message<I> message;

    private boolean acknowledged = false;

    public String getMessageId() {
        return message.getHeaders().get("MessageId", String.class);
    }

    public String getReceiptHandle() {
        return message.getHeaders().get("ReceiptHandle", String.class);
    }

    @SneakyThrows
    public synchronized void acknowledge() {
        Acknowledgement acknowledgment = message.getHeaders().get("Acknowledgment", Acknowledgement.class);
        if (acknowledgment == null) {
            throw new NullPointerException("there is no \"Acknowledgment\" in the message headers");
        }
        acknowledgment.acknowledgeAsync().get(2, TimeUnit.MINUTES);
        acknowledged = true;
    }

    @SneakyThrows
    public synchronized void changeMessageVisibility(SqsAsyncClient sqsClient, ChangeMessageVisibilityRequest request) {
        if (acknowledged) {
            return;
        }
        sqsClient.changeMessageVisibility(request).get();
    }
}
