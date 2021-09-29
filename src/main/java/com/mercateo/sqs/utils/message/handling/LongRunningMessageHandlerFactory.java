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

import com.mercateo.sqs.utils.queue.Queue;
import com.mercateo.sqs.utils.queue.QueueFactory;
import com.mercateo.sqs.utils.queue.QueueName;
import com.mercateo.sqs.utils.visibility.VisibilityTimeoutExtenderFactory;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.inject.Inject;
import javax.inject.Named;

import lombok.NonNull;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.cloud.aws.messaging.listener.SimpleMessageListenerContainer;

@Named
public class LongRunningMessageHandlerFactory implements DisposableBean {

    private final MessageHandlingRunnableFactory messageHandlingRunnableFactory;

    private final VisibilityTimeoutExtenderFactory timeoutExtenderFactory;

    private final QueueFactory queueFactory;

    private final ScheduledExecutorService executorService;

    // visible for testing
    final int maxNumberOfMessagesPerBatch;

    private final Set<LongRunningMessageHandler> longRunningMessageHandlers = new HashSet<>();

    @Inject
    public LongRunningMessageHandlerFactory(
            @NonNull MessageHandlingRunnableFactory messageHandlingRunnableFactory,
            @NonNull VisibilityTimeoutExtenderFactory timeoutExtenderFactory,
            @NonNull QueueFactory queueFactory,
            @NonNull SimpleMessageListenerContainer simpleMessageListenerContainer) {
        this.messageHandlingRunnableFactory = messageHandlingRunnableFactory;
        this.timeoutExtenderFactory = timeoutExtenderFactory;
        this.queueFactory = queueFactory;

        this.executorService = Executors.newScheduledThreadPool(1);

        this.maxNumberOfMessagesPerBatch = extractMaxNumberOfMessagesFromListenerContainer(
                simpleMessageListenerContainer);
    }

    private int extractMaxNumberOfMessagesFromListenerContainer(
            @NonNull SimpleMessageListenerContainer simpleMessageListenerContainer) {
        try {
            Field f = simpleMessageListenerContainer.getClass().getSuperclass().getDeclaredField(
                    "maxNumberOfMessages");
            f.setAccessible(true);
            Integer maxNumberOfMessages = (Integer) f.get(simpleMessageListenerContainer);
            if (maxNumberOfMessages != null) {
                return maxNumberOfMessages;
            } else {
                // org.springframework.cloud.aws.messaging.listener.AbstractMessageListenerContainer.DEFAULT_MAX_NUMBER_OF_MESSAGES
                return 10;
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException(
                    "Cannot get BatchSize of SimpleMessageListenerContainer", e);
        }
    }

    /**
     * Creates a handler which should be called for each incoming message and takes
     * care of extending the visibility timeout of that message and scheduling the
     * execution of the message processing.
     *
     * @param numberOfThreads
     *            number of concurrent workers that are allowed to run in parallel
     * @param worker
     *            the single worker instance that should do the processing of the
     *            message; should be stateless
     * @param queueName
     *            the name of the queue; required for timeout extension
     * @param finishedMessageCallback
     *            will be invoked when the processing of a message is completed in
     *            case logging or further steps have to be performed
     * @param timeUntilVisibilityTimeoutExtension
     *            the time between visibility timeout extensions; should be at least
     *            5 seconds smaller than the queue visibility timeout. Smaller
     *            values mean more frequent extensions of the timeout. A single
     *            extension sets the timeout to be equal to the original default
     *            visibility timeout
     * @param <I>
     *            the input type of the message payload
     * @param <O>
     *            the output type of the message processing
     * @return a LongRunningMessageHandler instance
     */
    public <I, O> LongRunningMessageHandler<I, O> get(int numberOfThreads,
            @NonNull MessageWorkerWithHeaders<I, O> worker, @NonNull QueueName queueName,
            @NonNull FinishedMessageCallback<I, O> finishedMessageCallback,
            @NonNull Duration timeUntilVisibilityTimeoutExtension) {
        return this.get(numberOfThreads, worker, queueName, finishedMessageCallback,
                timeUntilVisibilityTimeoutExtension, false);
    }

    /**
     * Creates a handler which should be called for each incoming message and takes
     * care of extending the visibility timeout of that message and scheduling the
     * execution of the message processing.
     *
     * @param numberOfThreads
     *            number of concurrent workers that are allowed to run in parallel
     * @param worker
     *            the single worker instance that should do the processing of the
     *            message; should be stateless
     * @param queueName
     *            the name of the queue; required for timeout extension
     * @param finishedMessageCallback
     *            will be invoked when the processing of a message is completed in
     *            case logging or further steps have to be performed
     * @param timeUntilVisibilityTimeoutExtension
     *            the time between visibility timeout extensions; should be at least
     *            5 seconds smaller than the queue visibility timeout. Smaller
     *            values mean more frequent extensions of the timeout. A single
     *            extension sets the timeout to be equal to the original default
     *            visibility timeout
     * @param waitForTasksToCompleteOnShutdown
     *            in case of application shutdown this specifies if the listener should wait until all tasks are completed (graceful shutdown)
     * @param <I>
     *            the input type of the message payload
     * @param <O>
     *            the output type of the message processing
     * @return a LongRunningMessageHandler instance
     */
    public <I, O> LongRunningMessageHandler<I, O> get(int numberOfThreads,
            @NonNull MessageWorkerWithHeaders<I, O> worker, @NonNull QueueName queueName,
            @NonNull FinishedMessageCallback<I, O> finishedMessageCallback,
            @NonNull Duration timeUntilVisibilityTimeoutExtension,
            boolean waitForTasksToCompleteOnShutdown) {

        Queue queue = queueFactory.get(queueName);
        LongRunningMessageHandler<I, O> longRunningMessageHandler = new LongRunningMessageHandler<>(executorService, maxNumberOfMessagesPerBatch,
                numberOfThreads, messageHandlingRunnableFactory, timeoutExtenderFactory, worker,
                queue, finishedMessageCallback, timeUntilVisibilityTimeoutExtension, waitForTasksToCompleteOnShutdown);
        longRunningMessageHandlers.add(longRunningMessageHandler);
        return longRunningMessageHandler;
    }

    /**
     * Creates a handler which should be called for each incoming message and takes
     * care of extending the visibility timeout of that message and scheduling the
     * execution of the message processing.
     *
     * @param numberOfThreads
     *            number of concurrent workers that are allowed to run in parallel
     * @param worker
     *            the single worker instance that should do the processing of the
     *            message; should be stateless
     * @param queueName
     *            the name of the queue; required for timeout extension
     * @param timeUntilVisibilityTimeoutExtension
     *            the time between visibility timeout extensions; should be at least
     *            5 seconds smaller than the queue visibility timeout. Smaller
     *            values mean more frequent extensions of the timeout. A single
     *            extension sets the timeout to be equal to the original default
     *            visibility timeout
     * @param <I>
     *            the input type of the message payload
     * @param <O>
     *            the output type of the message processing
     * @return a LongRunningMessageHandler instance
     */
    public <I, O> LongRunningMessageHandler<I, O> get(int numberOfThreads,
            @NonNull MessageWorkerWithHeaders<I, O> worker, @NonNull QueueName queueName,
            @NonNull Duration timeUntilVisibilityTimeoutExtension) {
        return this.get(numberOfThreads, worker, queueName, (input, output) -> {
        }, timeUntilVisibilityTimeoutExtension, false);
    }

    /**
     * Creates a handler which should be called for each incoming message and takes
     * care of extending the visibility timeout of that message and scheduling the
     * execution of the message processing.
     *
     * @param numberOfThreads
     *            number of concurrent workers that are allowed to run in parallel
     * @param worker
     *            the single worker instance that should do the processing of the
     *            message; should be stateless
     * @param queueName
     *            the name of the queue; required for timeout extension
     * @param timeUntilVisibilityTimeoutExtension
     *            the time between visibility timeout extensions; should be at least
     *            5 seconds smaller than the queue visibility timeout. Smaller
     *            values mean more frequent extensions of the timeout. A single
     *            extension sets the timeout to be equal to the original default
     *            visibility timeout
     * @param waitForTasksToCompleteOnShutdown
     *            in case of application shutdown this specifies if the listener should wait until all tasks are completed (graceful shutdown)
     * @param <I>
     *            the input type of the message payload
     * @param <O>
     *            the output type of the message processing
     * @return a LongRunningMessageHandler instance
     */
    public <I, O> LongRunningMessageHandler<I, O> get(int numberOfThreads,
            @NonNull MessageWorkerWithHeaders<I, O> worker, @NonNull QueueName queueName,
            @NonNull Duration timeUntilVisibilityTimeoutExtension,
            boolean waitForTasksToCompleteOnShutdown) {

        Queue queue = queueFactory.get(queueName);
        return new LongRunningMessageHandler<>(executorService, maxNumberOfMessagesPerBatch,
                numberOfThreads, messageHandlingRunnableFactory, timeoutExtenderFactory, worker,
                queue, (input, output) -> {
                }, timeUntilVisibilityTimeoutExtension, waitForTasksToCompleteOnShutdown);
    }

    @Override
    public void destroy() throws Exception {
        executorService.shutdown();
        longRunningMessageHandlers.forEach(LongRunningMessageHandler::close);
    }
}