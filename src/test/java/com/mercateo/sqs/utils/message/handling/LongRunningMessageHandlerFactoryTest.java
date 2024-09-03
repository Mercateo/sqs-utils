package com.mercateo.sqs.utils.message.handling;

import static org.assertj.core.api.Assertions.assertThat;

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
    void setUp() {
        MockitoAnnotations.openMocks(this);
        uut = new LongRunningMessageHandlerFactory(messageHandlingRunnableFactory,
                timeoutExtenderFactory, queueFactory);
    }

    @Test
    void testNullContracts() {
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
    void testConstructor_extractsTheCorrectMessageBatchSize() {
        // given
        int expectedBatchSize = 8;

        // when
        uut.setMaxConcurrentMessages(8);

        // then
        assertThat(uut.getMaxConcurrentMessages()).isEqualTo(expectedBatchSize);
    }
}