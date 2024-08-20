/**
 *
 * Copyright © 2017 Mercateo AG (http://www.mercateo.com)
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
package com.mercateo.sqs.utils.message.handling;

import io.awspring.cloud.messaging.listener.Acknowledgment;

import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import org.springframework.messaging.Message;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

@Slf4j
public class MessageHandlingRunnable<I, O> implements Runnable {

    private final MessageWorkerWithHeaders<I, O> worker;

    private final Message<I> message;

    private final FinishedMessageCallback<I, O> finishedMessageCallback;

    private final SetWithUpperBound<String> messages;

    private final ScheduledFuture<?> visibilityTimeoutExtender;

    private final ErrorHandlingStrategy<I> errorHandlingStrategy;

    MessageHandlingRunnable(@NonNull MessageWorkerWithHeaders<I, O> worker,
            @NonNull Message<I> message,
            @NonNull FinishedMessageCallback<I, O> finishedMessageCallback,
            @NonNull SetWithUpperBound<String> messages,
            @NonNull ScheduledFuture<?> visibilityTimeoutExtender,
            @NonNull ErrorHandlingStrategy<I> errorHandlingStrategy) {

        this.worker = worker;
        this.message = message;
        this.finishedMessageCallback = finishedMessageCallback;
        this.messages = messages;
        this.visibilityTimeoutExtender = visibilityTimeoutExtender;
        this.errorHandlingStrategy = errorHandlingStrategy;

    }

    @Override
    public void run() {
        String messageId = String.valueOf(message.getHeaders().get("id", UUID.class));
        Acknowledgement acknowledgment = message.getHeaders().get("Acknowledgment",
                Acknowledgement.class);
        try {
            log.info("starting processing of message " + messageId);

            O outcome = worker.work(message.getPayload(), message.getHeaders());

            finishedMessageCallback.call(message.getPayload(), outcome);
            acknowledge(messageId, acknowledgment);
            log.info("message task successfully processed and message acknowledged: " + messageId);
        } catch (InterruptedException e) {
            log.info("got interrupted, did not finish: " + messageId, e);
        } catch (Exception e) {
            errorHandlingStrategy.handleWorkerException(e, message);
            acknowledge(messageId, acknowledgment);
        } catch (Throwable t) {
            errorHandlingStrategy.handleWorkerThrowable(t, message);
            acknowledge(messageId, acknowledgment);
        } finally {
            visibilityTimeoutExtender.cancel(false);
            messages.remove(messageId);
        }
    }

    private void acknowledge(String messageId, Acknowledgment acknowledgment) {
        try {
            try {
                acknowledgment.acknowledge().get();
            } catch (AwsServiceException e) {
                errorHandlingStrategy.handleAcknowledgeMessageException(e, message);
            }
        } catch (Exception e) {
            log.error("failure during acknowledge " + messageId, e);
        }
    }
}