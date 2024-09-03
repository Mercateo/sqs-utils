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

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import lombok.NonNull;


@Named
public class LongRunningMessageHandlerFactory {

    private final MessageHandlingRunnableFactory messageHandlingRunnableFactory;

    private final VisibilityTimeoutExtenderFactory timeoutExtenderFactory;

    private final QueueFactory queueFactory;

    private final ScheduledExecutorService executorService;

    // visible for testing
    private int maxNumberOfMessagesPerBatch;

    @Inject
    public LongRunningMessageHandlerFactory(
            @NonNull MessageHandlingRunnableFactory messageHandlingRunnableFactory,
            @NonNull VisibilityTimeoutExtenderFactory timeoutExtenderFactory,
            @NonNull QueueFactory queueFactory) {
        this.messageHandlingRunnableFactory = messageHandlingRunnableFactory;
        this.timeoutExtenderFactory = timeoutExtenderFactory;
        this.queueFactory = queueFactory;

        this.executorService = Executors.newScheduledThreadPool(1,
                new ThreadFactory() {

                    private final ThreadFactory threadFactory = Executors.defaultThreadFactory();

                    private int createdThreads = 0;

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread newThread = threadFactory.newThread(r);
                        newThread.setName(LongRunningMessageHandlerFactory.class.getSimpleName() + "-" + createdThreads++);
                        return newThread;
                    }
                });

        this.maxNumberOfMessagesPerBatch = 10;
    }

    public void setMaxConcurrentMessages(Integer maxConcurrentMessages) {
        this.maxNumberOfMessagesPerBatch = maxConcurrentMessages;
    }

    public int getMaxConcurrentMessages() {
        return this.maxNumberOfMessagesPerBatch;
    }

    /**
     * Creates a handler which should be called for each incoming message and
     * takes care of extending the visibility timeout of that message and
     * scheduling the execution of the message processing.
     *
     * @param numberOfThreads
     *            number of concurrent workers that are allowed to run in
     *            parallel
     * @param worker
     *            the single worker instance that should do the processing of
     *            the message; should be stateless
     * @param queueName
     *            the name of the queue; required for timeout extension
     * @param finishedMessageCallback
     *            will be invoked when the processing of a message is completed
     *            in case logging or further steps have to be performed
     * @param timeUntilVisibilityTimeoutExtension
     *            the time between visibility timeout extensions; should be at
     *            least 5 seconds smaller than the queue visibility timeout.
     *            Smaller values mean more frequent extensions of the timeout. A
     *            single extension sets the timeout to be equal to the original
     *            default visibility timeout
     * @param <I>
     *            the input type of the message payload
     * @param <O>
     *            the output type of the message processing
     * @return a LongRunningMessageHandler instance
     */
    public <I, O> LongRunningMessageHandler<I, O> get(int numberOfThreads,
            @NonNull MessageWorkerWithHeaders<I, O> worker,
            @NonNull QueueName queueName,
            @NonNull FinishedMessageCallback<I, O> finishedMessageCallback,
            @NonNull Duration timeUntilVisibilityTimeoutExtension) {
        return this.get(numberOfThreads,
                worker,
                queueName,
                finishedMessageCallback,
                timeUntilVisibilityTimeoutExtension,
                Duration.ofSeconds(0));
    }

    /**
     * Creates a handler which should be called for each incoming message and
     * takes care of extending the visibility timeout of that message and
     * scheduling the execution of the message processing.
     *
     * @param numberOfThreads
     *            number of concurrent workers that are allowed to run in
     *            parallel
     * @param worker
     *            the single worker instance that should do the processing of
     *            the message; should be stateless
     * @param queueName
     *            the name of the queue; required for timeout extension
     * @param timeUntilVisibilityTimeoutExtension
     *            the time between visibility timeout extensions; should be at
     *            least 5 seconds smaller than the queue visibility timeout.
     *            Smaller values mean more frequent extensions of the timeout. A
     *            single extension sets the timeout to be equal to the original
     *            default visibility timeout
     * @param <I>
     *            the input type of the message payload
     * @param <O>
     *            the output type of the message processing
     * @return a LongRunningMessageHandler instance
     */
    public <I, O> LongRunningMessageHandler<I, O> get(int numberOfThreads,
            @NonNull MessageWorkerWithHeaders<I, O> worker, @NonNull QueueName queueName,
            @NonNull Duration timeUntilVisibilityTimeoutExtension) {
        return this.get(numberOfThreads,
                worker,
                queueName,
                (input, output) -> {
                },
                timeUntilVisibilityTimeoutExtension,
                Duration.ofSeconds(0));
    }

    /**
     * Creates a handler which should be called for each incoming message and
     * takes care of extending the visibility timeout of that message and
     * scheduling the execution of the message processing.
     *
     * @param numberOfThreads
     *            number of concurrent workers that are allowed to run in
     *            parallel
     * @param worker
     *            the single worker instance that should do the processing of
     *            the message; should be stateless
     * @param queueName
     *            the name of the queue; required for timeout extension
     * @param timeUntilVisibilityTimeoutExtension
     *            the time between visibility timeout extensions; should be at
     *            least 5 seconds smaller than the queue visibility timeout.
     *            Smaller values mean more frequent extensions of the timeout. A
     *            single extension sets the timeout to be equal to the original
     *            default visibility timeout
     * @param awaitShutDown
     *            in case of application shutdown this specifies the time frame
     *            during which the messages can try to finish processing; their
     *            processing is cancelled if they do not finish in time
     * @param <I>
     *            the input type of the message payload
     * @param <O>
     *            the output type of the message processing
     * @return a LongRunningMessageHandler instance
     */
    public <I, O> LongRunningMessageHandler<I, O> get(int numberOfThreads,
            @NonNull MessageWorkerWithHeaders<I, O> worker, @NonNull QueueName queueName,
            @NonNull Duration timeUntilVisibilityTimeoutExtension,
            @NonNull Duration awaitShutDown) {

        return this.get(numberOfThreads,
                worker,
                queueName,
                (input, output) -> {
                },
                timeUntilVisibilityTimeoutExtension,
                awaitShutDown);
    }

    /**
     * Creates a handler which should be called for each incoming message and
     * takes care of extending the visibility timeout of that message and
     * scheduling the execution of the message processing.
     *
     * @param numberOfThreads
     *            number of concurrent workers that are allowed to run in
     *            parallel
     * @param worker
     *            the single worker instance that should do the processing of
     *            the message; should be stateless
     * @param queueName
     *            the name of the queue; required for timeout extension
     * @param finishedMessageCallback
     *            will be invoked when the processing of a message is completed
     *            in case logging or further steps have to be performed
     * @param timeUntilVisibilityTimeoutExtension
     *            the time between visibility timeout extensions; should be at
     *            least 5 seconds smaller than the queue visibility timeout.
     *            Smaller values mean more frequent extensions of the timeout. A
     *            single extension sets the timeout to be equal to the original
     *            default visibility timeout
     * @param awaitShutDown
     *            in case of application shutdown this specifies the time frame
     *            during which the messages can try to finish processing; their
     *            processing is cancelled if they do not finish in time
     * @param <I>
     *            the input type of the message payload
     * @param <O>
     *            the output type of the message processing
     * @return a LongRunningMessageHandler instance
     */
    public <I, O> LongRunningMessageHandler<I, O> get(int numberOfThreads,
            @NonNull MessageWorkerWithHeaders<I, O> worker,
            @NonNull QueueName queueName,
            @NonNull FinishedMessageCallback<I, O> finishedMessageCallback,
            @NonNull Duration timeUntilVisibilityTimeoutExtension,
            @NonNull Duration awaitShutDown) {
        ErrorHandlingStrategy<I> errorHandlingStrategy = new DefaultErrorHandlingStrategy<I>();
        return this.get(numberOfThreads,
                worker,
                queueName,
                finishedMessageCallback,
                timeUntilVisibilityTimeoutExtension,
                awaitShutDown,
                errorHandlingStrategy);
    }

    /**
     * Creates a handler which should be called for each incoming message and
     * takes care of extending the visibility timeout of that message and
     * scheduling the execution of the message processing.
     *
     * @param numberOfThreads
     *            number of concurrent workers that are allowed to run in
     *            parallel
     * @param worker
     *            the single worker instance that should do the processing of
     *            the message; should be stateless
     * @param queueName
     *            the name of the queue; required for timeout extension
     * @param finishedMessageCallback
     *            will be invoked when the processing of a message is completed
     *            in case logging or further steps have to be performed
     * @param timeUntilVisibilityTimeoutExtension
     *            the time between visibility timeout extensions; should be at
     *            least 5 seconds smaller than the queue visibility timeout.
     *            Smaller values mean more frequent extensions of the timeout. A
     *            single extension sets the timeout to be equal to the original
     *            default visibility timeout
     * @param awaitShutDown
     *            in case of application shutdown this specifies the time frame
     *            during which the messages can try to finish processing; their
     *            processing is cancelled if they do not finish in time
     * @param <I>
     *            the input type of the message payload
     * @param <O>
     *            the output type of the message processing
     * 
     * @param errorHandlingStrategy
     *            defines how the exceptions that are inside the framework are
     *            handled, logged and propagated within the framework.
     * @return a LongRunningMessageHandler instance
     */
    public <I, O> LongRunningMessageHandler<I, O> get(int numberOfThreads,
            @NonNull MessageWorkerWithHeaders<I, O> worker,
            @NonNull QueueName queueName,
            @NonNull FinishedMessageCallback<I, O> finishedMessageCallback,
            @NonNull Duration timeUntilVisibilityTimeoutExtension,
            @NonNull Duration awaitShutDown,
            @NonNull ErrorHandlingStrategy<I> errorHandlingStrategy) {

        Queue queue = queueFactory.get(queueName);
        return new LongRunningMessageHandler<>(executorService,
                maxNumberOfMessagesPerBatch,
                numberOfThreads,
                messageHandlingRunnableFactory,
                timeoutExtenderFactory,
                worker,
                queue,
                finishedMessageCallback,
                timeUntilVisibilityTimeoutExtension,
                awaitShutDown,
                errorHandlingStrategy);
    }

    public void shutdown() {
        executorService.shutdownNow();
    }

}