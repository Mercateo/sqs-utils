/**
 * Copyright © 2017 Mercateo AG (http://www.mercateo.com)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mercateo.sqs.utils.visibility;

import software.amazon.awssdk.services.sqs.*;

import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.mercateo.sqs.utils.message.handling.ErrorHandlingStrategy;
import com.mercateo.sqs.utils.message.handling.MessageWrapper;
import com.mercateo.sqs.utils.queue.Queue;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import lombok.NonNull;

@Named
public class VisibilityTimeoutExtenderFactory {

    private final SqsAsyncClient sqsClient;

    @Inject
    public VisibilityTimeoutExtenderFactory(@NonNull SqsAsyncClient amazonSQS) {
        this.sqsClient = amazonSQS;
    }

    public VisibilityTimeoutExtender get(@NonNull MessageWrapper messageWrapper, @NonNull Queue queue,
            @NonNull ErrorHandlingStrategy<?> errorHandlingStrategy) {

        Duration defaultVisibilityTimeout = queue.getDefaultVisibilityTimeout();

        return new VisibilityTimeoutExtender(sqsClient, defaultVisibilityTimeout, messageWrapper, queue
                .getUrl(), errorHandlingStrategy,
                new RetryStrategy(WaitStrategies.fixedWait(1000, TimeUnit.MILLISECONDS),
                        StopStrategies.stopAfterAttempt(5)));
    }
}