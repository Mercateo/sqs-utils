package com.mercateo.sqs.utils.message.handling;

import com.google.common.testing.NullPointerTester;
import com.mercateo.sqs.utils.visibility.VisibilityTimeoutExtender;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class MessageHandlingRunnableFactoryTest {

    private MessageHandlingRunnableFactory uut;

    @BeforeEach
    public void setUp() throws Exception {
        uut = new MessageHandlingRunnableFactory();
    }

    @Test
    public void testNullContracts() throws Exception {
        // given
        NullPointerTester nullPointerTester = new NullPointerTester();
        nullPointerTester.setDefault(SetWithUpperBound.class, new SetWithUpperBound<Integer>(5));
        nullPointerTester.setDefault(VisibilityTimeoutExtender.class, Mockito.mock(
                VisibilityTimeoutExtender.class));

        // when
        nullPointerTester.testInstanceMethods(uut, NullPointerTester.Visibility.PACKAGE);
        nullPointerTester.testAllPublicConstructors(uut.getClass());
    }
}