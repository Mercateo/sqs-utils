package com.mercateo.sqs.utils.visibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityRequest;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.testing.NullPointerTester;
import com.mercateo.sqs.utils.message.handling.ErrorHandlingStrategy;

import java.net.UnknownHostException;
import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;

class VisibilityTimeoutExtenderTest {

    private VisibilityTimeoutExtender uut;

    @Mock
    private AmazonSQS sqsClient;

    @Mock
    private ErrorHandlingStrategy<?> errorHandlingStrategy;

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        HashMap<String, Object> headerMap = new HashMap<>();
        headerMap.put("ReceiptHandle", "rhd");
        GenericMessage<Object> message = new GenericMessage<>(new Object(), new MessageHeaders(
                headerMap));
        RetryStrategy retryStrategy = new RetryStrategy(WaitStrategies.fixedWait(1, TimeUnit.MICROSECONDS),
                StopStrategies.stopAfterAttempt(5));
        uut = new VisibilityTimeoutExtender(sqsClient, Duration.ofMinutes(10), message, "queue",
                errorHandlingStrategy, retryStrategy);
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

        assertThat(request.getReceiptHandle()).isEqualTo("rhd");
        assertThat(request.getQueueUrl()).isEqualTo("queue");
        assertThat(request.getVisibilityTimeout().intValue()).isEqualTo(600);

    }

    @Test
    void retryForUnknownHostException() {

        SdkClientException sdkClientException = new SdkClientException("foo", new UnknownHostException());

        // given
        when(sqsClient.changeMessageVisibility(any()))
                .thenThrow(sdkClientException);
        // when
        Throwable result = catchThrowable(() -> uut.run());

        // then
        assertThat(result).isInstanceOf(RuntimeException.class);
        assertThat(result.getCause()).isInstanceOf(RetryException.class);
        verify(sqsClient, times(5)).changeMessageVisibility(any());
    }

    @Test
    void dontRetryForSdkClientExceptionsInGeneral() {

        SdkClientException sdkClientException = new SdkClientException("foo");

        // given
        when(sqsClient.changeMessageVisibility(any())).thenThrow(sdkClientException);
        // when
        Throwable result = catchThrowable(() -> uut.run());

        // then
        assertThat(result).isInstanceOf(RuntimeException.class);
        verify(sqsClient, times(1)).changeMessageVisibility(any());
    }

}