# sqs-utils [![Build Status](https://travis-ci.org/Mercateo/sqs-utils.svg?branch=master)](https://travis-ci.org/Mercateo/sqs-utils) [![Coverage Status](https://coveralls.io/repos/github/Mercateo/sqs-utils/badge.svg)](https://coveralls.io/github/Mercateo/sqs-utils?branch=master) [![MavenCentral](https://img.shields.io/maven-central/v/com.mercateo.sqs/sqs-utils.svg)](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.mercateo.sqs%22%20AND%20a%3A%22sqs-utils%22)

Provides a way to process multiple long running SQS messages at the same time with as little as possible downtime for assigned worker threads. During processing the message visibility timeout is extended so that message will stay in flight and will not be redelivered. Depending on the number of assigned local workers further SQS ReceiveMessageRequests can be performed before all messages have completed processing.

 
# Usage

```
@Named
public class MyQueueMessageListener {
 
    private static final String QUEUE_NAME = "your-queue-name-here";
 
    private final LongRunningMessageHandlerFactory<InputDataType, OutputDataType> messageHandler;
    
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
```

`// 1` Call `LongRunningMessageHandlerFactory#get(...)` to get get instance of the handler, pass in the number of concurrent message you want the worker to handle. This can be independent of the number of messages you receive per batch from SQS. You also have to specify the interval of timeout extension calls (has to be at least 5 seconds smaller than the default visibility timeout of the queue). The `worker` has to be stateless since it is called from multiple threads for multiple messages.

`// 2` In your `SqsListener` annotation you have to specify a `deletionPolicy = SqsMessageDeletionPolicy.NEVER` because returning from the `listenOnQueue` method does not guarantee completed processing of the message but only that the message has been scheduled for processing. The library takes care of deleting the message after processing of the message similiar to the behaviour of `SqsMessageDeletionPolicy.ON_SUCCESS`.

`// 3` Pass the message and the headers to the `handleMessage` method, the headers are needed for extracting the `MessageId` to identify messages internally, the `ReceiptHandle` to extend the timeout and the `Acknowledgement` to delete the message in the end. 


# Explanation

The `org.springframework.cloud.aws.messaging.listener.SimpleMessageListenerContainer` has a configurable `maxNumberOfMessages` to receive from a single `ReceiveMessageRequest` to aws.
After X number of messages are received they are dispatched to X workers, the spring framework then waits for all messages to be successfully completed and only then initiates a new `ReceiveMessageRequest`. 
That technique has at least one major flaw: one long running message blocks the other workers from processing more small messages since no new messages are received until all messages have finished processing.

Our library provides a way of dealing with that situation by having a fixed number of workers work on the messages independent of `maxNumberOfMessages`. An internal queue / buffer keeps track of all messages scheduled for processing and knows when a worker is idle. As soon as a single worker is free and the queue is empty the library enables spring to initiate a new `ReceiveMessageRequest`. 

In case of long running messages it is advised to use a `maxNumberOfMessages` of `1`, in particular if multiple instances of the listener are running, e.g. in EC2. If the number of configured workers is higher than the `maxNumberOfMessages` that means that multiple `ReceiveMessageRequest` can be performed while messages from the first request are already in processing. If you do not want any messages in the internal buffer of the library you should set `maxNumberOfMessages` to `1` which will guarantee that all messages are actually being processed, none are waiting.
