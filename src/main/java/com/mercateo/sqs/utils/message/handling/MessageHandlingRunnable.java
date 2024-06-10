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

import com.amazonaws.AmazonServiceException;

import io.awspring.cloud.messaging.listener.Acknowledgment;

import java.util.concurrent.ScheduledFuture;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.messaging.MessageHeaders;

@Slf4j
@RequiredArgsConstructor
public class MessageHandlingRunnable<I, O> implements Runnable {

    private final MessageWorkerWithHeaders<I, O> worker;

    private final MessageWrapper<I> messageWrapper;

    private final FinishedMessageCallback<I, O> finishedMessageCallback;

    private final SetWithUpperBound<String> messages;

    private final ScheduledFuture<?> visibilityTimeoutExtender;

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
            acknowledge(messageWrapper);
            log.info("message task successfully processed and message acknowledged: " + messageId); //
        } catch (InterruptedException e) {
            log.info("got interrupted, did not finish: " + messageId, e);
        } catch (Exception e) {
            errorHandlingStrategy.handleWorkerException(e, messageWrapper.getMessage());
            acknowledge(messageWrapper);
        } catch (Throwable t) {
            errorHandlingStrategy.handleWorkerThrowable(t, messageWrapper.getMessage());
            acknowledge(messageWrapper);
        } finally {
            visibilityTimeoutExtender.cancel(false);
            messages.remove(messageId);
        }
    }

    private void acknowledge(MessageWrapper<I> messageWrapper) {
        Acknowledgment acknowledgment = messageWrapper.getAcknowledgment();
        try {
            try {
                acknowledgment.acknowledge().get();
                messageWrapper.acknowledge();
            } catch (AmazonServiceException e) {
                errorHandlingStrategy.handleAcknowledgeMessageException(e, messageWrapper.getMessage());
            }
        } catch (Exception e) {
            log.error("failure during acknowledge " + messageWrapper.getMessageId(), e);
        }
    }
}