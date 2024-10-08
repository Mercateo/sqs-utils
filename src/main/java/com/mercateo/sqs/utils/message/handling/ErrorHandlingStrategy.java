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

import software.amazon.awssdk.awscore.exception.AwsServiceException;

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
    void handleWorkerException(Exception e, MessageWrapper<I> message);
    
    /**
     * Defines how a throwable, that is thrown by the worker are handled. If a
     * message should not be acknowledged, then exception must be thrown.
     *
     * @param t
     *            the throwable thrown by the worker
     * @param message
     *            that was incorrectly processed
     */
    void handleWorkerThrowable(Throwable t, MessageWrapper<I> message);

    /**
     * Defines how exceptions, that are thrown by the timeout extension are handled.
     * A possible reason for this can be that the max timeout of 12 hours is reached. 
     * Throwing an exception will stop any furthers tries to extend the timeout.
     * 
     * @param e the exception thrown by the aws call
     * @param  message that was tried to extend
     */
    
    void handleExtendVisibilityTimeoutException(AwsServiceException e, MessageWrapper<?> message);
    
    /**
     * Defines how exceptions, that are thrown by the message acknowledgement are handled.
     * A possible reason for this can be an invalid receipt handle or an already deleted message. 
     * Throwing an exception will be caught and logged as an error. Recommendation is not to throw an exception here.
     * 
     * @param e the exception thrown by the aws call
     * @param message that was tried to extend
     */
    
    void handleAcknowledgeMessageException(AwsServiceException e, MessageWrapper<I> message);

}
