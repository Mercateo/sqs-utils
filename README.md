# sqs-utils [![Build Status](https://travis-ci.org/Mercateo/sqs-utils.svg?branch=master)](https://travis-ci.org/Mercateo/sqs-utils) [![Coverage Status](https://coveralls.io/repos/github/Mercateo/sqs-utils/badge.svg)](https://coveralls.io/github/Mercateo/sqs-utils?branch=master) [![MavenCentral](https://img.shields.io/maven-central/v/com.mercateo.sqs/sqs-utils.svg)](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.mercateo.sqs%22%20AND%20a%3A%22sqs-utils%22)


Provides a way to process multiple long running SQS messages at the same time. During processing the message visibility timeout is extended so that message will stay in flight and will not be redelivered. Depending on the number of assigned local workers further SQS ReceiveMessageRequests can be performed before all messages have completed processing. 

Usage:
1. `@Inject LongRunningtMessageHandlerFactory` to get an instance of the factory for creating a message handler
2. Call `LongRunningMessageHandlerFactory#get(...)` to get get instance of the handler
3. Create a `@SqsListener`-annotated method like so:
```
    @SqsListener(value = yourQueueNameHere, deletionPolicy = SqsMessageDeletionPolicy.NEVER)
    public void listen(@NonNull @Payload YourMessageTypeHere command,
            @NonNull MessageHeaders messageHeaders) {

        GenericMessage<YourMessageTypeHere> message = new GenericMessage<>(command, messageHeaders);
        longRunningMessageHandler.handleMessage(message);
    }
```
`deletePolicy` is important because the message must not be deleted when this method returns, the library takes care of deleting the message after it was processed successfully. 

More information: how to call `get` correctly, worker has to be stateless. 