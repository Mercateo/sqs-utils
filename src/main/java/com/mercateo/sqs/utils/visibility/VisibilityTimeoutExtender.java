/**
 * Copyright © 2017 Mercateo AG (http://www.mercateo.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mercateo.sqs.utils.visibility;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.sqs.*;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;

import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.mercateo.sqs.utils.message.handling.ErrorHandlingStrategy;
import com.mercateo.sqs.utils.message.handling.MessageWrapper;

import java.net.UnknownHostException;
import java.time.Duration;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VisibilityTimeoutExtender implements Runnable {

    private final SqsAsyncClient sqsClient;

    private final ChangeMessageVisibilityRequest request;

    private final MessageWrapper<?> messageWrapper;

    private final ErrorHandlingStrategy<?> errorHandlingStrategy;

    private final Retryer<Void> retryer;

    VisibilityTimeoutExtender(@NonNull SqsAsyncClient sqsClient, @NonNull Duration newVisibilityTimeout,
            @NonNull MessageWrapper<?> messageWrapper, @NonNull String queueUrl,
            @NonNull ErrorHandlingStrategy<?> errorHandlingStrategy,
            @NonNull RetryStrategy retryStrategy) {
        this.sqsClient = sqsClient;
        this.messageWrapper = messageWrapper;
        this.errorHandlingStrategy = errorHandlingStrategy;
        this.retryer = RetryerBuilder
                .<Void> newBuilder()
                .retryIfException(t -> (t.getCause() instanceof UnknownHostException))
                .withWaitStrategy(retryStrategy.getRetryWaitStrategy())
                .withStopStrategy(retryStrategy.getRetryStopStrategy())
                .build();

        request = ChangeMessageVisibilityRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(messageWrapper.getReceiptHandle())
                .visibilityTimeout(timeoutInSeconds(newVisibilityTimeout))
                .build();
    }

    private Integer timeoutInSeconds(Duration timeout) {
        return (int) timeout.getSeconds();
    }

    @Override
    public void run() {
        try {
            retryer.call(() -> {
                messageWrapper.changeMessageVisibility(sqsClient, request);
                return null;
            });
        } catch (AwsServiceException e) {
            errorHandlingStrategy.handleExtendVisibilityTimeoutException(e, messageWrapper);
        } catch (Exception e) {
            log.error("error while extending message visibility for {}", messageWrapper.getMessageId(), e);
            throw new RuntimeException(e);
        }
    }
}