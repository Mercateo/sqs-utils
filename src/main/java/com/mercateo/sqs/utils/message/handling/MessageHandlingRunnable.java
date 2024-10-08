/**
 * Copyright © 2017 Mercateo AG (http://www.mercateo.com)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mercateo.sqs.utils.message.handling;

import java.util.concurrent.ScheduledFuture;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.messaging.MessageHeaders;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

@Slf4j
@RequiredArgsConstructor
public class MessageHandlingRunnable<I, O> implements Runnable {

    @NonNull
    private final MessageWorkerWithHeaders<I, O> worker;

    @NonNull
    private final MessageWrapper<I> messageWrapper;

    @NonNull
    private final FinishedMessageCallback<I, O> finishedMessageCallback;

    @NonNull
    private final SetWithUpperBound<String> messages;

    @NonNull
    private final ScheduledFuture<?> visibilityTimeoutExtender;

    @NonNull
    private final ErrorHandlingStrategy<I> errorHandlingStrategy;

    @Override
    public void run() {
        String messageId = messageWrapper.getMessageId();
        try {
            log.info("starting processing of message " + messageId);

            I payload = messageWrapper.getMessage().getPayload();
            MessageHeaders headers = messageWrapper.getMessage().getHeaders();
            O outcome = worker.work(payload, headers);

            finishedMessageCallback.call(payload, outcome);
            acknowledge();
            log.info("message task successfully processed and message acknowledged: " + messageId);
        } catch (InterruptedException e) {
            log.info("got interrupted, did not finish: " + messageId, e);
        } catch (Exception e) {
            errorHandlingStrategy.handleWorkerException(e, messageWrapper);
            acknowledge();
        } catch (Throwable t) {
            errorHandlingStrategy.handleWorkerThrowable(t, messageWrapper);
            acknowledge();
        } finally {
            visibilityTimeoutExtender.cancel(false);
            messages.remove(messageId);
        }
    }

    private void acknowledge() {
        try {
            messageWrapper.acknowledge();
        } catch (AwsServiceException e) {
            errorHandlingStrategy.handleAcknowledgeMessageException(e, messageWrapper);
        } catch (Exception e) {
            log.error("failure during acknowledge " + messageWrapper.getMessageId(), e);
        }
    }
}