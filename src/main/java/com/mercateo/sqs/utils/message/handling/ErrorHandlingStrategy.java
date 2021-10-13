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

import org.springframework.messaging.Message;

public interface ErrorHandlingStrategy<I> {

    /**
     * Defines how exceptions, that are thrown by the worker are handled. If a
     * message should not be acknowledged, then exception must be thrown.
     * 
     * @param e
     *            the exception thrown by the worker
     * @param message
     *            that was incorrectly processed
     */
    void handle(Exception e, Message<I> message);

}
