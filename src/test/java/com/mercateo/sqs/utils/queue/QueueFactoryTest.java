package com.mercateo.sqs.utils.queue;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.sqs.model.GetQueueAttributesResult;
import com.amazonaws.services.sqs.model.GetQueueUrlResult;
import com.amazonaws.services.sqs.model.QueueAttributeName;
import com.google.common.testing.NullPointerTester;

import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;

public class QueueFactoryTest {

    @Mock
    private SqsClient amazonSQS;

    private QueueFactory uut;

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        uut = new QueueFactory(amazonSQS);
    }

    @Test
    public void testNullContracts() throws Exception {
        // given
        NullPointerTester nullPointerTester = new NullPointerTester();

        // when
        nullPointerTester.testInstanceMethods(uut, NullPointerTester.Visibility.PACKAGE);
        nullPointerTester.testAllPublicConstructors(uut.getClass());
    }

    @Test
    public void testGet() {
        // given
        QueueName qn = new QueueName("q1");
        GetQueueUrlResponse queueUrlResult = mock(GetQueueUrlResponse.class);
        when(queueUrlResult.queueUrl()).thenReturn("url1");
        GetQueueAttributesResponse attributesResult = mock(GetQueueAttributesResponse.class);
        HashMap<software.amazon.awssdk.services.sqs.model.QueueAttributeName, String> attributes = new HashMap<>();
        attributes.put(software.amazon.awssdk.services.sqs.model.QueueAttributeName.fromValue("1"), "3");
        attributes.put(software.amazon.awssdk.services.sqs.model.QueueAttributeName.fromValue("hi"), "ho");
        when(attributesResult.attributes()).thenReturn(attributes);
        when(amazonSQS.getQueueUrl(any(GetQueueUrlRequest.class))).thenReturn(queueUrlResult);
        when(amazonSQS.getQueueAttributes(any(GetQueueAttributesRequest.class))).thenReturn(
                attributesResult);

        // when
        Queue queue = uut.get(qn);

        // then
        assertEquals("url1", queue.getUrl());
        assertEquals("q1", queue.getName().getId());
        assertEquals(attributes, queue.getQueueAttributes());

    }
}