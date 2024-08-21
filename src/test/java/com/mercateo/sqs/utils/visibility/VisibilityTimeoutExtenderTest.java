package com.mercateo.sqs.utils.visibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.testing.NullPointerTester;
import com.mercateo.sqs.utils.message.handling.ErrorHandlingStrategy;

import java.net.UnknownHostException;
import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityResponse;

class VisibilityTimeoutExtenderTest {

    private VisibilityTimeoutExtender uut;

    @Mock
    private SqsAsyncClient sqsClient;

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
        uut = new VisibilityTimeoutExtender(sqsClient, Duration.ofSeconds(10*60), message, "queue",
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
    void testRun() throws ExecutionException, InterruptedException {
        // given
        CompletableFuture<ChangeMessageVisibilityResponse> future = new CompletableFuture<>();
        future.complete(ChangeMessageVisibilityResponse.builder().build());
        when(sqsClient.changeMessageVisibility(any(ChangeMessageVisibilityRequest.class))).thenReturn(future);

        // when
        uut.run();

        // then
        ArgumentCaptor<software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest> captor = ArgumentCaptor.forClass(
                software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest.class);
        verify(sqsClient).changeMessageVisibility(captor.capture());
        ChangeMessageVisibilityRequest request = captor.getValue();

        assertThat(request.receiptHandle()).isEqualTo("rhd");
        assertThat(request.queueUrl()).isEqualTo("queue");
        assertThat(request.visibilityTimeout().intValue()).isEqualTo(600);

    }

    @Test
    void retryForUnknownHostException() {

        SdkClientException sdkClientException =
                SdkClientException.builder().cause(new UnknownHostException()).build();

        // given
        when(sqsClient.changeMessageVisibility(any(ChangeMessageVisibilityRequest.class)))
                .thenThrow(sdkClientException);
        // when
        Throwable result = catchThrowable(() -> uut.run());

        // then
        assertThat(result).isInstanceOf(RuntimeException.class);
        assertThat(result.getCause()).isInstanceOf(RetryException.class);
        verify(sqsClient, times(5)).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
    }

    @Test
    void dontRetryForSdkClientExceptionsInGeneral() {

        SdkClientException sdkClientException = SdkClientException.builder().build();

        // given
        when(sqsClient.changeMessageVisibility(any(ChangeMessageVisibilityRequest.class))).thenThrow(sdkClientException);
        // when
        Throwable result = catchThrowable(() -> uut.run());

        // then
        assertThat(result).isInstanceOf(RuntimeException.class);
        verify(sqsClient, times(1)).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
    }

}