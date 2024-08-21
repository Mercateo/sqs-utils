package com.mercateo.sqs.utils.message.handling;

import static org.junit.Assert.assertEquals;

import com.google.common.testing.NullPointerTester;
import com.mercateo.sqs.utils.queue.QueueFactory;
import com.mercateo.sqs.utils.queue.QueueName;
import com.mercateo.sqs.utils.visibility.VisibilityTimeoutExtenderFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class LongRunningMessageHandlerFactoryTest {

    @Mock
    private MessageHandlingRunnableFactory messageHandlingRunnableFactory;

    @Mock
    private VisibilityTimeoutExtenderFactory timeoutExtenderFactory;

    @Mock
    private QueueFactory queueFactory;

    private LongRunningMessageHandlerFactory uut;

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        uut = new LongRunningMessageHandlerFactory(messageHandlingRunnableFactory,
                timeoutExtenderFactory, queueFactory);
    }

    @Test
    public void testNullContracts() throws Exception {
        // given
        NullPointerTester nullPointerTester = new NullPointerTester();
        nullPointerTester.setDefault(QueueName.class, new QueueName("name"));
        nullPointerTester.setDefault(VisibilityTimeoutExtenderFactory.class,
                timeoutExtenderFactory);
        nullPointerTester.setDefault(QueueFactory.class, queueFactory);
        nullPointerTester.setDefault(Integer.class, 10);

        // when
        nullPointerTester.testInstanceMethods(uut, NullPointerTester.Visibility.PACKAGE);
        nullPointerTester.testAllPublicConstructors(uut.getClass());
    }

    @Test
    public void testConstructor_extractsTheCorrectMessageBatchSize() {
        // given

        // when
        uut = new LongRunningMessageHandlerFactory(messageHandlingRunnableFactory,
                timeoutExtenderFactory, queueFactory);
        uut.setMaxConcurrentMessages(12);

        // then
        assertEquals(12, uut.maxNumberOfMessagesPerBatch);
    }
}