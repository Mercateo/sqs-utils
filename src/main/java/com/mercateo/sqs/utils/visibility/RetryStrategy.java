package com.mercateo.sqs.utils.visibility;

import com.github.rholder.retry.StopStrategy;
import com.github.rholder.retry.WaitStrategy;

import lombok.NonNull;
import lombok.Value;

@Value
public class RetryStrategy {

    @NonNull
    WaitStrategy retryWaitStrategy;

    @NonNull
    StopStrategy retryStopStrategy;
}
