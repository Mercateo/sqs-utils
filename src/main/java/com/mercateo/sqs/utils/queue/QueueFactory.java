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

import javax.inject.Inject;
import javax.inject.Named;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.GetQueueUrlRequest;

import lombok.NonNull;

@Named
public class QueueFactory {

    private final AmazonSQS amazonSQS;

    @Inject
    public QueueFactory(@NonNull AmazonSQS amazonSQS) {
        this.amazonSQS = amazonSQS;
    }

    public Queue get(@NonNull QueueName queueName) {
        GetQueueUrlRequest urlRequest = new GetQueueUrlRequest().withQueueName(queueName.getId());
        String queueUrl = amazonSQS.getQueueUrl(urlRequest).getQueueUrl();

        GetQueueAttributesRequest attributesRequest = new GetQueueAttributesRequest(queueUrl,
                Collections.singletonList("All"));
        Map<String, String> attributes = amazonSQS.getQueueAttributes(attributesRequest)
                .getAttributes();

        return new Queue(queueName, queueUrl, attributes);
    }
}