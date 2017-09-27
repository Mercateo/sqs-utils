package com.mercateo.sqs.utils.queue;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesResult;
import com.amazonaws.services.sqs.model.GetQueueUrlRequest;
import com.amazonaws.services.sqs.model.GetQueueUrlResult;
import com.google.common.testing.NullPointerTester;

public class QueueFactoryTest {

    @Mock
    private AmazonSQS amazonSQS;

    private QueueFactory uut;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
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
        GetQueueUrlResult queueUrlResult = mock(GetQueueUrlResult.class);
        when(queueUrlResult.getQueueUrl()).thenReturn("url1");
        GetQueueAttributesResult attributesResult = mock(GetQueueAttributesResult.class);
        HashMap<String, String> attributes = new HashMap<>();
        attributes.put("1", "3");
        attributes.put("hi", "ho");
        when(attributesResult.getAttributes()).thenReturn(attributes);
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