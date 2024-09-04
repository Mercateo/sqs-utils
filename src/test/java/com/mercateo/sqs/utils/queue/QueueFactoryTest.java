package com.mercateo.sqs.utils.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.testing.NullPointerTester;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.*;

public class QueueFactoryTest {

    @Mock
    private SqsAsyncClient amazonSQS;

    private QueueFactory uut;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        uut = new QueueFactory(amazonSQS);
    }

    @Test
    void testNullContracts() throws Exception {
        // given
        NullPointerTester nullPointerTester = new NullPointerTester();

        // when
        nullPointerTester.testInstanceMethods(uut, NullPointerTester.Visibility.PACKAGE);
        nullPointerTester.testAllPublicConstructors(uut.getClass());
    }

    @Test
    void testGet() {
        // given
        QueueName qn = new QueueName("q1");
        GetQueueUrlResponse queueUrlResult = mock(GetQueueUrlResponse.class);
        when(queueUrlResult.queueUrl()).thenReturn("url1");
        CompletableFuture<GetQueueUrlResponse> mockGetQueueUrlResult = new CompletableFuture<>();
        mockGetQueueUrlResult.complete(queueUrlResult);

        GetQueueAttributesResponse attributesResult = mock(GetQueueAttributesResponse.class);
        HashMap<QueueAttributeName, String> attributes = new HashMap<>();
        attributes.put(QueueAttributeName.fromValue("1"), "3");
        attributes.put(QueueAttributeName.fromValue("hi"), "ho");
        CompletableFuture<GetQueueAttributesResponse> mockGetQueueAttributesResult = new CompletableFuture<>();
        mockGetQueueAttributesResult.complete(attributesResult);

        when(attributesResult.attributes()).thenReturn(attributes);
        when(amazonSQS.getQueueUrl(any(GetQueueUrlRequest.class))).thenReturn(mockGetQueueUrlResult);
        when(amazonSQS.getQueueAttributes(any(GetQueueAttributesRequest.class))).thenReturn(
                mockGetQueueAttributesResult);

        // when
        Queue queue = uut.get(qn);

        // then
        assertThat(queue.getUrl()).isEqualTo("url1");
        assertThat(queue.getName().getId()).isEqualTo("q1");
        assertThat(queue.getQueueAttributes()).isEqualTo(attributes);
    }
}