package com.mercateo.sqs.utils.message.handling;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.inject.Inject;
import javax.inject.Named;

import org.springframework.cloud.aws.messaging.listener.SimpleMessageListenerContainer;

import com.mercateo.sqs.utils.queue.Queue;
import com.mercateo.sqs.utils.queue.QueueFactory;
import com.mercateo.sqs.utils.queue.QueueName;
import com.mercateo.sqs.utils.visibility.VisibilityTimeoutExtenderFactory;

import lombok.NonNull;

@Named
public class LongRunningMessageHandlerFactory {

    private final MessageHandlingRunnableFactory messageHandlingRunnableFactory;

    private final VisibilityTimeoutExtenderFactory timeoutExtenderFactory;

    private final QueueFactory queueFactory;

    private final ScheduledExecutorService executorService;

    // visible for testing
    final int maxNumberOfMessagesPerBatch;

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
                timeUntilVisibilityTimeoutExtension, Duration.ofSeconds(0));
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
            @NonNull FinishedMessageCallback<I, O> finishedMessageCallback,
            @NonNull Duration timeUntilVisibilityTimeoutExtension,
            @NonNull Duration awaitShutDown) {

        Queue queue = queueFactory.get(queueName);
        return new LongRunningMessageHandler<>(executorService, maxNumberOfMessagesPerBatch,
                numberOfThreads, messageHandlingRunnableFactory, timeoutExtenderFactory, worker,
                queue, finishedMessageCallback, timeUntilVisibilityTimeoutExtension, awaitShutDown);
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
        }, timeUntilVisibilityTimeoutExtension, Duration.ofSeconds(0));
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

        Queue queue = queueFactory.get(queueName);
        return new LongRunningMessageHandler<>(executorService, maxNumberOfMessagesPerBatch,
                numberOfThreads, messageHandlingRunnableFactory, timeoutExtenderFactory, worker,
                queue, (input, output) -> {
                }, timeUntilVisibilityTimeoutExtension, awaitShutDown);
    }
}