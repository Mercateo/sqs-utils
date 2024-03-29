/**
 * Copyright © 2017 Mercateo AG (http://www.mercateo.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mercateo.sqs.utils.visibility;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityRequest;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityResult;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.mercateo.sqs.utils.message.handling.ErrorHandlingStrategy;

import java.net.UnknownHostException;
import java.time.Duration;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import org.springframework.messaging.Message;

@Slf4j
public class VisibilityTimeoutExtender implements Runnable {

    private final AmazonSQS sqsClient;

    private final ChangeMessageVisibilityRequest request;

    private final Message<?> message;

    private final ErrorHandlingStrategy<?> errorHandlingStrategy;

    private final Retryer<ChangeMessageVisibilityResult> retryer;

    VisibilityTimeoutExtender(@NonNull AmazonSQS sqsClient, @NonNull Duration newVisibilityTimeout,
            @NonNull Message<?> message, @NonNull String queueUrl,
            @NonNull ErrorHandlingStrategy<?> errorHandlingStrategy,
            @NonNull RetryStrategy retryStrategy) {
        this.sqsClient = sqsClient;
        this.message = message;
        this.errorHandlingStrategy = errorHandlingStrategy;
        this.retryer = RetryerBuilder
                .<ChangeMessageVisibilityResult> newBuilder()
                .retryIfException(t -> (t.getCause() instanceof UnknownHostException))
                .withWaitStrategy(retryStrategy.getRetryWaitStrategy())
                .withStopStrategy(retryStrategy.getRetryStopStrategy())
                .build();

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
            retryer.call(() -> sqsClient.changeMessageVisibility(request));
        } catch (AmazonServiceException e) {
            errorHandlingStrategy.handleExtendVisibilityTimeoutException(e, message);
        } catch (Exception e) {
            log.error("error while extending message visibility for " + message.getHeaders().get("MessageId",
                    String.class), e);
            throw new RuntimeException(e);
        }
    }
}