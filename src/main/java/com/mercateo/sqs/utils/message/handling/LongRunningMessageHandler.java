package com.mercateo.sqs.utils.message.handling;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.messaging.Message;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.mercateo.sqs.utils.queue.Queue;
import com.mercateo.sqs.utils.visibility.VisibilityTimeoutExtender;
import com.mercateo.sqs.utils.visibility.VisibilityTimeoutExtenderFactory;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LongRunningMessageHandler<I, O> {

    private final ThreadPoolTaskExecutor messageProcessingExecutor;

    private final MessageHandlingRunnableFactory messageHandlingRunnableFactory;

    private final VisibilityTimeoutExtenderFactory timeoutExtenderFactory;

    private final MessageWorker<I, O> worker;

    private final Queue queue;

    private final FinishedMessageCallback<I, O> finishedMessageCallback;

    private final SetWithUpperBound<String> messagesInProcessing;

    private final Duration timeUntilVisibilityTimeoutExtension;

    private final ScheduledExecutorService timeoutExtensionExecutor;

    LongRunningMessageHandler(@NonNull ScheduledExecutorService timeoutExtensionExecutor,
            int maxNumberOfMessages, int numberOfThreads,
            @NonNull MessageHandlingRunnableFactory messageHandlingRunnableFactory,
            @NonNull VisibilityTimeoutExtenderFactory timeoutExtenderFactory,
            @NonNull MessageWorker<I, O> worker, @NonNull Queue queue,
            @NonNull FinishedMessageCallback<I, O> finishedMessageCallback,
            @NonNull Duration timeUntilVisibilityTimeoutExtension,
            @NonNull Duration awaitShutDown) {
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

        messageProcessingExecutor = new ThreadPoolTaskExecutor();
        messageProcessingExecutor.setMaxPoolSize(numberOfThreads);
        messageProcessingExecutor.setCorePoolSize(numberOfThreads);
        /*
         * Since we only accept new messages if one slot in the messagesInProcessing-Set
         * / executor is free we can schedule at least one message for instant execution
         * while (maxNumberOfMessages - 1) will be put into the queue
         */
        messageProcessingExecutor.setQueueCapacity(maxNumberOfMessages - 1);
        messageProcessingExecutor.setAwaitTerminationSeconds((int) awaitShutDown.getSeconds());
        if (awaitShutDown.getSeconds() > 0) {
            Runtime.getRuntime().addShutdownHook(new Thread(messageProcessingExecutor::shutdown));
        }
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
     * <p>Returns iff there is at least one free slot in the internal executor i.e.
     * that new messages can be consumed. That way we guarantee that we can handle
     * an incoming maxNumberOfMessages on the next iteration. Returning from this
     * method does <b>not</b> mean the message has already been processed, it simply
     * means that it is in processing.
     *
     * <p>This method should only be called from a single thread, from a single
     * SqsListener and only once per message.
     *
     * <p>The SimpleMessageListenerContainer dispatches one task per incoming message
     * to an internal ThreadPoolExecutor and waits for all the tasks to finish
     * before polling from SQS again. That means we can block each task / thread
     * from returning until a free worker is available without interfering with the
     * dispatching of other message tasks.
     *
     * @param message
     *            the message to be processed
     */
    public void handleMessage(@NonNull Message<I> message) {
        String messageId = message.getHeaders().get("MessageId", String.class);
        if (messagesInProcessing.contains(messageId)) {
            return;
        }
        messagesInProcessing.add(messageId);

        ScheduledFuture<?> timeoutExtender;
        try {
            timeoutExtender = scheduleNewVisibilityTimeoutExtender(message);
        } catch (RuntimeException rex) {
            messagesInProcessing.remove(messageId);
            log.error("error while trying to schedule timeout extender", rex);
            throw new RuntimeException(rex);
        }

        try {
            scheduleNewMessageTask(message, timeoutExtender);
        } catch (RuntimeException rex) {
            messagesInProcessing.remove(messageId);
            timeoutExtender.cancel(true);
            log.error("error while trying to submit message processing task", rex);
            throw new RuntimeException(rex);
        }

        messagesInProcessing.waitUntilAtLeastOneFree();
    }

    private void scheduleNewMessageTask(@NonNull Message<I> message,
            ScheduledFuture<?> visibilityTimeoutExtender) {
        MessageHandlingRunnable<I, O> messageTask = messageHandlingRunnableFactory.get(worker,
                message, finishedMessageCallback, messagesInProcessing, visibilityTimeoutExtender);

        messageProcessingExecutor.submit(messageTask);
    }

    private ScheduledFuture<?> scheduleNewVisibilityTimeoutExtender(@NonNull Message<I> message) {
        VisibilityTimeoutExtender timeoutExtender = timeoutExtenderFactory.get(message, queue);
        return timeoutExtensionExecutor.scheduleAtFixedRate(timeoutExtender,
                timeUntilVisibilityTimeoutExtension.toMillis(), timeUntilVisibilityTimeoutExtension
                        .toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Visible for Testing
     *
     * @return Set containing messageIds in processing
     */
    SetWithUpperBound<String> getMessagesInProcessing() {
        return messagesInProcessing;
    }
}