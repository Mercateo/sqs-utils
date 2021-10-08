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

import lombok.extern.slf4j.Slf4j;

import org.springframework.messaging.Message;

@Slf4j
class RethrowAndLogStrategy<I> implements ErrorHandlingStrategy<I> {

    @Override
    public void filterNonDLQExceptions(Exception e, Message<I> message) {
        throw new RuntimeException(e);
    }

    @Override
    public void handleDLQExceptions(Exception e, Message<I> message) {
        String messageId = message.getHeaders().get("MessageId", String.class);
        log.error("error while handling message " + messageId + ": " + message.getPayload(), e);
    }

}
