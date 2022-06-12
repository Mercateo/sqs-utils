package com.mercateo.sqs.utils.visibility;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityRequest;
import com.google.common.testing.NullPointerTester;
import com.mercateo.sqs.utils.message.handling.ErrorHandlingStrategy;

import java.time.Duration;
import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;

class VisibilityTimeoutExtenderTest {

    @Mock
    private AmazonSQS sqsClient;

    private VisibilityTimeoutExtender uut;

    @Mock
    private ErrorHandlingStrategy<?> errorHandlingStrategy;

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        HashMap<String, Object> headerMap = new HashMap<>();
        headerMap.put("ReceiptHandle", "rhd");
        GenericMessage<Object> message = new GenericMessage<>(new Object(), new MessageHeaders(
                headerMap));
        uut = new VisibilityTimeoutExtender(sqsClient, Duration.ofMinutes(10), message, "queue", errorHandlingStrategy);
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
    void testRun() {
        // given

        // when
        uut.run();

        // then
        ArgumentCaptor<ChangeMessageVisibilityRequest> captor = ArgumentCaptor.forClass(
                ChangeMessageVisibilityRequest.class);
        verify(sqsClient).changeMessageVisibility(captor.capture());
        ChangeMessageVisibilityRequest request = captor.getValue();

        assertEquals("rhd", request.getReceiptHandle());
        assertEquals("queue", request.getQueueUrl());
        assertEquals(600, request.getVisibilityTimeout().intValue());
    }

}