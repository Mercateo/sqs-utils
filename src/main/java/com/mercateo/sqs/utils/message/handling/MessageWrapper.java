package com.mercateo.sqs.utils.message.handling;

import io.awspring.cloud.sqs.MessagingHeaders;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.awspring.cloud.sqs.listener.acknowledgement.AcknowledgementCallback;
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
        return String.valueOf(message.getHeaders().get("id", UUID.class));
    }

    public String getReceiptHandle() {
        return message.getHeaders().get("ReceiptHandle", String.class);
    }

    @SneakyThrows
    public synchronized void acknowledge() {
        AcknowledgementCallback<I> acknowledgementCallback = message.getHeaders().get(
                MessagingHeaders.ACKNOWLEDGMENT_CALLBACK_HEADER, AcknowledgementCallback.class);
        if (acknowledgementCallback == null) {
            throw new NullPointerException("There is no \"AcknowledgementCallback\" in the message headers");
        }
        try {
            acknowledgementCallback.onAcknowledge(message).get(2, TimeUnit.MINUTES);
            acknowledged = true;
        } catch (Exception e) {
            throw new RuntimeException("Failed to acknowledge message", e);
        }
    }

    @SneakyThrows
    public synchronized void changeMessageVisibility(SqsAsyncClient sqsClient, ChangeMessageVisibilityRequest request) {
        if (acknowledged) {
            return;
        }
        sqsClient.changeMessageVisibility(request).get();
    }
}
