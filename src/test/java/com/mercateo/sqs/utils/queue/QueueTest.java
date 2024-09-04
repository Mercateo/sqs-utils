package com.mercateo.sqs.utils.queue;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.testing.NullPointerTester;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

public class QueueTest {

    @Mock
    private Map<QueueAttributeName, String> queueAttributes;

    private Queue uut;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        uut = new Queue(new QueueName("123"), "http://url.de", queueAttributes);
    }

    @Test
    void testNullContracts() throws Exception {
        // given
        NullPointerTester nullPointerTester = new NullPointerTester();
        nullPointerTester.ignore(uut.getClass().getDeclaredMethod("canEqual", Object.class));
        nullPointerTester.ignore(uut.getClass().getDeclaredMethod("equals", Object.class));
        nullPointerTester.setDefault(QueueName.class, new QueueName("asiudb"));

        // when
        nullPointerTester.testInstanceMethods(uut, NullPointerTester.Visibility.PACKAGE);
        nullPointerTester.testAllPublicConstructors(uut.getClass());
    }

    @Test
    void testGetDefaultVisibilityTimeout() {
        // given
        Mockito.when(queueAttributes.get(QueueAttributeName.VISIBILITY_TIMEOUT)).thenReturn("734");

        // when
        Duration defaultVisibilityTimeout = uut.getDefaultVisibilityTimeout();

        // then
        assertThat(defaultVisibilityTimeout.getSeconds()).isEqualTo(734);
    }
}