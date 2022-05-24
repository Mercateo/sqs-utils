# 0.4.0

* shutdown in `LongRunningMessageHandler` and `LongRunningMessageHandlerFactory` must be explicitely called
    * you must shut down all `LongRunningMessageHandler` and then `LongRunningMessageHandlerFactory` because `LongRunningMessageHandlerFactory` contains the threads to extend the message visibilities

# 0.3.0

* provide functionality for long running SQS listener (up to 12 hours, the [maximum for SQS visibility](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html))
