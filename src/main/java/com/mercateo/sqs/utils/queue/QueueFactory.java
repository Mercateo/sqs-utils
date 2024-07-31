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
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;

@Named
public class QueueFactory {

    private final SqsClient amazonSQS;

    @Inject
    public QueueFactory(@NonNull SqsClient amazonSQS) {
        this.amazonSQS = amazonSQS;
    }

    public Queue get(@NonNull QueueName queueName) {
        software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest urlRequest = GetQueueUrlRequest.builder()
                .queueName(queueName.getId())
                .build();
        String queueUrl = amazonSQS.getQueueUrl(urlRequest).queueUrl();

        software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest attributesRequest = GetQueueAttributesRequest
                .builder()
                .queueUrl(queueUrl)
                .attributeNamesWithStrings(Collections.singletonList("All")).build();
        Map<String, String> attributes = amazonSQS.getQueueAttributes(attributesRequest)
                .attributesAsStrings();

        return new Queue(queueName, queueUrl, attributes);
    }
}