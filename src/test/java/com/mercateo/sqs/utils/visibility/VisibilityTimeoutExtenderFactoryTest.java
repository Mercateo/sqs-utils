package com.mercateo.sqs.utils.visibility;

import com.amazonaws.services.sqs.AmazonSQS;
import com.google.common.testing.NullPointerTester;
import com.mercateo.sqs.utils.queue.Queue;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class VisibilityTimeoutExtenderFactoryTest {

    @Mock
    private AmazonSQS amazonSQS;

    private VisibilityTimeoutExtenderFactory uut;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
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