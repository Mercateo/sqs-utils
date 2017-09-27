package com.mercateo.sqs.utils.queue;

import java.time.Duration;
import java.util.Map;

import lombok.Data;
import lombok.NonNull;

@Data
public class Queue {

    @NonNull
    private final QueueName name;

    @NonNull
    private final String url;

    @NonNull
    private final Map<String, String> queueAttributes;

    public Duration getDefaultVisibilityTimeout() {
        return Duration.ofSeconds(Integer.parseInt(queueAttributes.get("VisibilityTimeout")));
    }
}