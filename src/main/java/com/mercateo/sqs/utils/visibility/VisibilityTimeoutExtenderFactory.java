package com.mercateo.sqs.utils.visibility;

import java.time.Duration;

import javax.inject.Inject;
import javax.inject.Named;

import org.springframework.messaging.Message;

import com.amazonaws.services.sqs.AmazonSQS;
import com.mercateo.sqs.utils.queue.Queue;

import lombok.NonNull;

@Named
public class VisibilityTimeoutExtenderFactory {

    private final AmazonSQS sqsClient;

    @Inject
    public VisibilityTimeoutExtenderFactory(@NonNull AmazonSQS amazonSQS) {
        this.sqsClient = amazonSQS;
    }

    public VisibilityTimeoutExtender get(@NonNull Message<?> message, @NonNull Queue queue) {

        Duration defaultVisibilityTimeout = queue.getDefaultVisibilityTimeout();

        return new VisibilityTimeoutExtender(sqsClient, defaultVisibilityTimeout, message, queue
                .getUrl());
    }
}