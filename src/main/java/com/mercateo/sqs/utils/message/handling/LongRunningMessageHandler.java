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
import com.mercateo.sqs.utils.visibility.VisibilityTimeoutExtenderFactory;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import org.springframework.messaging.Message;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
public class LongRunningMessageHandler<I, O> {

    private final ThreadPoolTaskExecutor messageProcessingExecutor;

    private final MessageHandlingRunnableFactory messageHandlingRunnableFactory;

    private final VisibilityTimeoutExtenderFactory timeoutExtenderFactory;

    private final MessageWorkerWithHeaders<I, O> worker;

    private final Queue queue;

    private final FinishedMessageCallback<I, O> finishedMessageCallback;

    private final SetWithUpperBound<String> messagesInProcessing;

    private final Duration timeUntilVisibilityTimeoutExtension;

    private final ScheduledExecutorService timeoutExtensionExecutor;
    
    private final ErrorHandlingStrategy<I> errorHandlingStrategy;

    private final Duration awaitShutDown;

    LongRunningMessageHandler(@NonNull ScheduledExecutorService timeoutExtensionExecutor,
            int maxNumberOfMessages, int numberOfThreads,
            @NonNull MessageHandlingRunnableFactory messageHandlingRunnableFactory,
            @NonNull VisibilityTimeoutExtenderFactory timeoutExtenderFactory,
            @NonNull MessageWorkerWithHeaders<I, O> worker, @NonNull Queue queue,
            @NonNull FinishedMessageCallback<I, O> finishedMessageCallback,
            @NonNull Duration timeUntilVisibilityTimeoutExtension,
            @NonNull Duration awaitShutDown,
            @NonNull ErrorHandlingStrategy<I> errorHandlingStrategy) {
        if (timeUntilVisibilityTimeoutExtension.isZero() || timeUntilVisibilityTimeoutExtension
                .isNegative()) {
            throw new IllegalArgumentException("the timeout has to be > 0");
        }
        this.timeoutExtensionExecutor = timeoutExtensionExecutor;
        this.messageHandlingRunnableFactory = messageHandlingRunnableFactory;
        this.timeoutExtenderFactory = timeoutExtenderFactory;
        this.worker = worker;
        this.queue = queue;
        this.finishedMessageCallback = finishedMessageCallback;
        this.timeUntilVisibilityTimeoutExtension = timeUntilVisibilityTimeoutExtension;
        this.awaitShutDown = awaitShutDown;
        this.errorHandlingStrategy = errorHandlingStrategy;

        messageProcessingExecutor = new ThreadPoolTaskExecutor();
        messageProcessingExecutor.setCorePoolSize(numberOfThreads);
        messageProcessingExecutor.setMaxPoolSize(numberOfThreads);
        messageProcessingExecutor.setThreadNamePrefix(getClass().getSimpleName()+"-"+queue.getName().getId()+"-");
        /*
         * Since we only accept new messages if one slot in the messagesInProcessing-Set
         * / executor is free we can schedule at least one message for instant execution
         * while (maxNumberOfMessages - 1) will be put into the queue
         */
        messageProcessingExecutor.setQueueCapacity(maxNumberOfMessages - 1);
        messageProcessingExecutor.afterPropertiesSet();

        messagesInProcessing = new SetWithUpperBound<>(numberOfThreads);

        if (queue.getDefaultVisibilityTimeout().minusSeconds(5).compareTo(
                timeUntilVisibilityTimeoutExtension) < 0) {
            throw new IllegalStateException("The extension interval of "
                    + timeUntilVisibilityTimeoutExtension.getSeconds()
                    + " is too close to the VisibilityTimeout of " + queue
                            .getDefaultVisibilityTimeout().getSeconds()
                    + " seconds of the queue, has to be at least 5 seconds less.");
        }
    }

    /**
     * Submits a task for the processing of the message into the internal executor.
     * Schedules a timeoutExtender that takes care of extending the visibility
     * timeout until and during message processing.
     *
     * <p>
     * Returns iff there is at least one free slot in the internal executor i.e.
     * that new messages can be consumed. That way we guarantee that we can handle
     * an incoming maxNumberOfMessages on the next iteration. Returning from this
     * method does <b>not</b> mean the message has already been processed, it simply
     * means that it is in processing.
     *
     * <p>
     * This method should only be called from a single thread, from a single
     * SqsListener and only once per message.
     *
     * <p>
     * The SimpleMessageListenerContainer dispatches one task per incoming message
     * to an internal ThreadPoolExecutor and waits for all the tasks to finish
     * before polling from SQS again. That means we can block each task / thread
     * from returning until a free worker is available without interfering with the
     * dispatching of other message tasks.
     *
     * @param message
     *            the message to be processed
     */
    public void handleMessage(@NonNull Message<I> message) {
        MessageWrapper<I> messageWrapper = new MessageWrapper<>(message);
        String messageId = messageWrapper.getMessageId();

        if (messagesInProcessing.contains(messageId)) {
            return;
        }
        messagesInProcessing.add(messageId);

        ScheduledFuture<?> timeoutExtender;
        try {
            timeoutExtender = scheduleNewVisibilityTimeoutExtender(messageWrapper);
        } catch (RuntimeException rex) {
            messagesInProcessing.remove(messageId);
            log.error("error while trying to schedule timeout extender", rex);
            throw new RuntimeException(rex);
        }

        try {
            scheduleNewMessageTask(messageWrapper, timeoutExtender);
        } catch (RuntimeException rex) {
            messagesInProcessing.remove(messageId);
            timeoutExtender.cancel(true);
            log.error("error while trying to submit message processing task", rex);
            throw new RuntimeException(rex);
        }

        messagesInProcessing.waitUntilAtLeastOneFree();
    }

    /** 
     * Returns the number of threads that are currently not busy working on messages.
     * <p>
     * The method is intended as a workaround for message visibility extension 
     * and message acknowledge errors, but can only be applied, when you have
     * control over the messages listener (i.e. not using the Spring one) and
     * you can actually use it.
     * See https://github.com/Mercateo/sqs-utils/issues/16 for details
     * 
     * @return Number of threads that are currently not processing messages
     */
    public int getFreeWorkerCapacity() {
        return messagesInProcessing.free();
    }

    private void scheduleNewMessageTask(@NonNull MessageWrapper<I> message,
            ScheduledFuture<?> visibilityTimeoutExtender) {
        MessageHandlingRunnable<I, O> messageTask = messageHandlingRunnableFactory.get(worker,
                message, finishedMessageCallback, messagesInProcessing, visibilityTimeoutExtender, errorHandlingStrategy);

        messageProcessingExecutor.submit(messageTask);
    }

    private ScheduledFuture<?> scheduleNewVisibilityTimeoutExtender(@NonNull MessageWrapper<I> message) {
        return timeoutExtensionExecutor.scheduleAtFixedRate(
                timeoutExtenderFactory.get(message, queue, errorHandlingStrategy),
                timeUntilVisibilityTimeoutExtension.toMillis(),
                timeUntilVisibilityTimeoutExtension.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    /**
     * Visible for Testing
     *
     * @return Set containing messageIds in processing
     */
    SetWithUpperBound<String> getMessagesInProcessing() {
        return messagesInProcessing;
    }

    @SneakyThrows
    public void shutdown() {
        messageProcessingExecutor.getThreadPoolExecutor().shutdown();
        boolean successfullyTerminated = messageProcessingExecutor.getThreadPoolExecutor().awaitTermination(awaitShutDown.getSeconds(), TimeUnit.SECONDS);
        if (!successfullyTerminated) {
            messageProcessingExecutor.getThreadPoolExecutor().shutdownNow();
            messageProcessingExecutor.getThreadPoolExecutor().awaitTermination(10, TimeUnit.SECONDS);
        }
    }
    /**
     * Returns the number of elements that the {@link #messageProcessingExecutor} can accept
     * <p>
     * This method should only be used in a single threaded environment, since it is possible that
     * in a multi-threaded environment the number of free slots changes between the call to this method
     *
     * @return Remaining queue capacity
     */
    public int getRemainingCapacity() {
        return messageProcessingExecutor
                .getThreadPoolExecutor()
                .getQueue()
                .remainingCapacity();
    }

}