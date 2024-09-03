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
package com.mercateo.sqs.utils.message.handling;

import java.util.Objects;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import software.amazon.awssdk.awscore.exception.AwsServiceException;

@Slf4j
class DefaultErrorHandlingStrategy<I> implements ErrorHandlingStrategy<I> {

    @Override
    @SneakyThrows
    public void handleWorkerException(Exception e, MessageWrapper<I> message) {
        log.error("error while handling message " + message.getMessageId() + ": " + message.getMessage().getPayload(), e);
        throw e;
    }

    @Override
    @SneakyThrows
    public void handleWorkerThrowable(Throwable t, MessageWrapper<I> message) {
        String messageId = String.valueOf(message.getMessageId());
        log.error("error while handling message " + messageId + ": " + message.getMessage().getPayload(), t);
        throw t;
    }

    @Override
    public void handleExtendVisibilityTimeoutException(AwsServiceException e,
            MessageWrapper<?> message) {

        String msg = "error while extending message visibility for " + Objects.requireNonNull(
                message.getMessageId());
        log.error(msg, e);
        throw e;

    }

    @Override
    public void handleAcknowledgeMessageException(AwsServiceException e, MessageWrapper<I> message) {
        String messageId = message.getMessageId();
        log.error("could not acknowledge " + messageId, e);
    }

}
