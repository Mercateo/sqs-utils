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
package com.mercateo.sqs.utils.visibility;

import com.amazonaws.services.sqs.AmazonSQS;
import com.mercateo.sqs.utils.queue.Queue;

import java.time.Duration;

import javax.inject.Inject;
import javax.inject.Named;

import lombok.NonNull;

import org.springframework.messaging.Message;

@Named
public class VisibilityTimeoutExtenderFactory {

    private final AmazonSQS sqsClient;

    @Inject
    public VisibilityTimeoutExtenderFactory(@NonNull AmazonSQS amazonSQS) {
        this.sqsClient = amazonSQS;
    }

    public VisibilityTimeoutExtender get(@NonNull Message<?> message, @NonNull Queue queue) {

        Duration defaultVisibilityTimeout = queue.getDefaultVisibilityTimeout();

        return new VisibilityTimeoutExtender(sqsClient, defaultVisibilityTimeout, message, queue
                .getUrl());
    }
}