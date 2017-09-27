package com.mercateo.sqs.utils.visibility;

import java.time.Duration;

import org.springframework.messaging.Message;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityRequest;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VisibilityTimeoutExtender implements Runnable {

    private final AmazonSQS sqsClient;

    private final ChangeMessageVisibilityRequest request;

    VisibilityTimeoutExtender(@NonNull AmazonSQS sqsClient, @NonNull Duration newVisibilityTimeout,
            @NonNull Message<?> message, @NonNull String queueUrl) {
        this.sqsClient = sqsClient;

        request = new ChangeMessageVisibilityRequest().withQueueUrl(queueUrl).withReceiptHandle(
                message.getHeaders().get("ReceiptHandle", String.class)).withVisibilityTimeout(
                        timeoutInSeconds(newVisibilityTimeout));
    }

    private Integer timeoutInSeconds(Duration timeout) {
        return (int) timeout.getSeconds();
    }

    @Override
    public void run() {
        try {
            log.trace("changing message visibility: " + request);
            sqsClient.changeMessageVisibility(request);
        } catch (Exception e) {
            log.error("error while extending message visibility", e);
            throw new RuntimeException(e);
        }
    }
}