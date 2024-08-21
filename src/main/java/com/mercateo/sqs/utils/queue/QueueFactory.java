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
package com.mercateo.sqs.utils.queue;

import java.util.Collections;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import lombok.NonNull;
import lombok.SneakyThrows;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

@Named
public class QueueFactory {

    private final SqsAsyncClient amazonSQS;

    @Inject
    public QueueFactory(@NonNull SqsAsyncClient amazonSQS) {
        this.amazonSQS = amazonSQS;
    }

    @SneakyThrows
    public Queue get(@NonNull QueueName queueName) {
        GetQueueUrlRequest urlRequest = GetQueueUrlRequest.builder()
                .queueName(queueName.getId())
                .build();
        String queueUrl = amazonSQS.getQueueUrl(urlRequest).get().queueUrl();

        GetQueueAttributesRequest attributesRequest = GetQueueAttributesRequest
                .builder()
                .queueUrl(queueUrl)
                .attributeNamesWithStrings(Collections.singletonList("All")).build();
        Map<QueueAttributeName, String> attributes = amazonSQS.getQueueAttributes(attributesRequest)
                .get().attributes();

        return new Queue(queueName, queueUrl, attributes);
    }
}