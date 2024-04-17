# 0.8.0

* pass MDC context to workers

# 0.7.1

* adds the possibility to configure a handling in case of a `Throwable` in `ErrorHandlingStrategy`

# 0.6.0

* Visibility timeout and acknowledge workaround
* Fix deployment by explicitly activating artifact signing

# 0.5.0

* adds the possibility to configure the error handling of the framework even more. Additionaly to exceptions thrown by the worker, you can now define error handling routines for "extend timeout" and "acknowledge message" errors. Exception handling for users that don't already define their own `ErrorHandlingStrategy` stays the same.

# 0.4.0

* shutdown in `LongRunningMessageHandler` and `LongRunningMessageHandlerFactory` must be explicitely called
    * you must shut down all `LongRunningMessageHandler` and then `LongRunningMessageHandlerFactory` because `LongRunningMessageHandlerFactory` contains the threads to extend the message visibilities

# 0.3.0

* provide functionality for long running SQS listener (up to 12 hours, the [maximum for SQS visibility](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html))
