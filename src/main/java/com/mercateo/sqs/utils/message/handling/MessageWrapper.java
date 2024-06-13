package com.mercateo.sqs.utils.message.handling;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityRequest;

import io.awspring.cloud.messaging.listener.Acknowledgment;

import java.util.concurrent.TimeUnit;

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

    private boolean acknowledged = false;

    public String getMessageId() {
        return message.getHeaders().get("MessageId", String.class);
    }

    public String getReceiptHandle() {
        return message.getHeaders().get("ReceiptHandle", String.class);
    }

    @SneakyThrows
    public synchronized void acknowledge() {
        Acknowledgment acknowledgment = message.getHeaders().get("Acknowledgment", Acknowledgment.class);
        if (acknowledgment == null) {
            throw new NullPointerException("there is no \"Acknowledgment\" in the message headers");
        }
        acknowledgment.acknowledge().get(2, TimeUnit.MINUTES);
        acknowledged = true;
    }

    public synchronized void changeMessageVisibility(AmazonSQS sqsClient, ChangeMessageVisibilityRequest request) {
        if (acknowledged) {
            return;
        }
        sqsClient.changeMessageVisibility(request);
    }
}
