# sqs-utils [![Build Status](https://travis-ci.org/Mercateo/sqs-utils.svg?branch=master)](https://travis-ci.org/Mercateo/sqs-utils) [![Coverage Status](https://coveralls.io/repos/github/Mercateo/sqs-utils/badge.svg)](https://coveralls.io/github/Mercateo/sqs-utils?branch=master) [![MavenCentral](https://img.shields.io/maven-central/v/com.mercateo.sqs/sqs-utils.svg)](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.mercateo.sqs%22%20AND%20a%3A%22sqs-utils%22)

Provides a way to process multiple long running SQS messages at the same time with as little as possible downtime for assigned worker threads. During processing the message visibility timeout is extended so that message will stay in flight and will not be redelivered.

 
# Usage

```
@Named
public class MyQueueMessageListener {
 
    private static final String QUEUE_NAME = "your-queue-name-here";
 
    private final LongRunningMessageHandler<InputDataType, OutputDataType> messageHandler;
    
    @Inject
    MyQueueMessageListener(
            @NonNull LongRunningMessageHandlerFactory messageHandlerFactory,
            @NonNull MyMessageWorker worker,
            @NonNull MyMessageFinisher finisher) {
            
        // 1
        this.messageHandler = messageHandlerFactory.get(4, worker, 
                new QueueName(QUEUE_NAME), finisher, 
                Duration.ofSeconds(240));
    }
 
    // 2
    @SqsListener(value = QUEUE_NAME, deletionPolicy = SqsMessageDeletionPolicy.NEVER)
    public void listenOnQueue(
            @NonNull @Payload InputDataType command,
            @NonNull MessageHeaders messageHeaders) {
 
        // 3
        GenericMessage<InputDataType> message = new GenericMessage<>(command, messageHeaders);
        messageHandler.handleMessage(message);
    }
}

@Named
public class MyMessageWorker implements MessageWorker<InputDataType, OutputDataType> {
    @Override
    public OutputDataType work(@NonNull InputDataType input) {
        // process the `input` and produce some `output`
        return output;
    }
}

@Named
public class MyMessageFinisher implements FinishedMessageCallback<InputDataType, OutputDataType> {
    @Override
    public void call(@NonNull InputDataType input, @NonNull OutputDataType output) {
        // worker completed, do some optional cleanup
    }
}
```

* `// 1` Call `LongRunningMessageHandlerFactory#get(...)` to get an instance of the handler and pass in the number of concurrent messages you want the worker to handle. This can be independent of the number of messages you receive per batch from SQS. You also have to specify the interval of timeout extension calls (has to be at least 5 seconds smaller than the default visibility timeout of the queue). The `worker` has to be stateless since it is called from multiple threads for multiple messages.

* `// 2` In your `SqsListener` annotation you have to specify a `deletionPolicy = SqsMessageDeletionPolicy.NEVER` because returning from the `listenOnQueue` method does not guarantee completed processing of the message but only that the message has been scheduled for processing. The library takes care of deleting the message after processing of the message similiar to the behaviour of `SqsMessageDeletionPolicy.ON_SUCCESS`.

* `// 3` Pass the message and the headers to the `handleMessage` method. The headers are required because the library needs the `MessageId` to identify messages internally, the `ReceiptHandle` to extend the timeout and the `Acknowledgement` to delete the message in the end. This method call causes the message to be scheduled for execution. 


# Explanation

The `org.springframework.cloud.aws.messaging.listener.SimpleMessageListenerContainer` has a configurable `maxNumberOfMessages` to receive from a single `ReceiveMessageRequest` to SQS.
After N messages are received they are dispatched to N workers, the spring framework then waits for all messages to be successfully completed and only then initiates a new `ReceiveMessageRequest`. 
That technique has at least one major flaw: one long running message blocks the other workers from processing more small messages since no new messages are received until all messages have finished processing.

Our library provides a way of dealing with that situation by having a fixed number of workers work on the messages independent of `maxNumberOfMessages`. An internal buffer keeps track of all messages scheduled for processing and knows when a worker is idle. As soon as a single worker is free and the buffer is empty the library enables spring to initiate a new `ReceiveMessageRequest`. The buffer has size `maxNumberOfMessages - 1` because at most `maxNumberOfMessages` can be received, one is guaranteed to be instantly worked on, while the rest will be put on hold.

In case of long running messages it is advised to configure `maxNumberOfMessages = 1`, in particular if multiple instances of the listener are running, e.g. in EC2. That way the buffer has size 0 and it is guaranteed that all received messages will be processed right away, no message will be waiting. That means no message is in-flight without actually being worked on, enabling other instances to receive those messages.

If the number of configured workers is higher than the `maxNumberOfMessages` that means that multiple `ReceiveMessageRequest`s can be performed while messages from previous requests are already in processing. This results in concurrent processing of multiple message while keeping the number of idle messages smaller.
