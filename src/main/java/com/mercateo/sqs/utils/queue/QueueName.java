package com.mercateo.sqs.utils.queue;

import java.io.Serializable;

import lombok.Data;
import lombok.NonNull;

@Data
public class QueueName implements Serializable {

    private static final long serialVersionUID = 1L;

    @NonNull
    private final String id;
}