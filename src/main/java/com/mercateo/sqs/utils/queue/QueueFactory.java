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