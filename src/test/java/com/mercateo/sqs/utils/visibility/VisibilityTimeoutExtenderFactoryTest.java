package com.mercateo.sqs.utils.visibility;

import com.google.common.testing.NullPointerTester;
import com.mercateo.sqs.utils.queue.Queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

public class VisibilityTimeoutExtenderFactoryTest {

    @Mock
    private SqsAsyncClient amazonSQS;

    private VisibilityTimeoutExtenderFactory uut;

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        uut = new VisibilityTimeoutExtenderFactory(amazonSQS);
    }

    @Test
    public void testNullContracts() throws Exception {
        // given
        NullPointerTester nullPointerTester = new NullPointerTester();
        nullPointerTester.setDefault(Queue.class, Mockito.mock(Queue.class));

        // when
        nullPointerTester.testInstanceMethods(uut, NullPointerTester.Visibility.PACKAGE);
        nullPointerTester.testAllPublicConstructors(uut.getClass());
    }
}