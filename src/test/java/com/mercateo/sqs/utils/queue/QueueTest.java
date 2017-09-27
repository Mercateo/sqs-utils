package com.mercateo.sqs.utils.queue;

import static org.junit.Assert.assertEquals;

import java.time.Duration;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.google.common.testing.NullPointerTester;

public class QueueTest {

    @Mock
    private Map<String, String> queueAttributes;

    private Queue uut;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        uut = new Queue(new QueueName("123"), "http://url.de", queueAttributes);
    }

    @Test
    public void testNullContracts() throws Exception {
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
    public void testGetDefaultVisibilityTimeout() {
        // given
        Mockito.when(queueAttributes.get("VisibilityTimeout")).thenReturn("734");

        // when
        Duration defaultVisibilityTimeout = uut.getDefaultVisibilityTimeout();

        // then
        assertEquals(734, defaultVisibilityTimeout.getSeconds());
    }
}