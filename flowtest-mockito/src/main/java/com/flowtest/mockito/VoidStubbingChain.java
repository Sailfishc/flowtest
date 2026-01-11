package com.flowtest.mockito;

import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.Stubber;

/**
 * Fluent chain for configuring void method stubbing behavior.
 *
 * @param <T> the type of the mock
 */
public class VoidStubbingChain<T> {

    private final MockConfiguration<T> parent;
    private final MockConfiguration.ThrowingConsumer<T> methodCall;

    public VoidStubbingChain(MockConfiguration<T> parent, MockConfiguration.ThrowingConsumer<T> methodCall) {
        this.parent = parent;
        this.methodCall = methodCall;
    }

    /**
     * Configures the void method to do nothing (default behavior).
     */
    public MockConfiguration<T> thenDoNothing() {
        try {
            Stubber stubber = Mockito.doNothing();
            methodCall.accept(stubber.when(parent.getMock()));
        } catch (Throwable e) {
            throw new RuntimeException("Error setting up void stubbing", e);
        }
        return parent;
    }

    /**
     * Configures the void method to throw the specified exception.
     */
    public MockConfiguration<T> thenThrow(Throwable throwable) {
        try {
            Stubber stubber = Mockito.doThrow(throwable);
            methodCall.accept(stubber.when(parent.getMock()));
        } catch (Throwable e) {
            throw new RuntimeException("Error setting up void stubbing", e);
        }
        return parent;
    }

    /**
     * Configures the void method to use a custom answer.
     */
    public MockConfiguration<T> thenAnswer(Answer<?> answer) {
        try {
            Stubber stubber = Mockito.doAnswer(answer);
            methodCall.accept(stubber.when(parent.getMock()));
        } catch (Throwable e) {
            throw new RuntimeException("Error setting up void stubbing", e);
        }
        return parent;
    }

    /**
     * Configures the void method to call the real method (for spies).
     */
    public MockConfiguration<T> thenCallRealMethod() {
        try {
            Stubber stubber = Mockito.doCallRealMethod();
            methodCall.accept(stubber.when(parent.getMock()));
        } catch (Throwable e) {
            throw new RuntimeException("Error setting up void stubbing", e);
        }
        return parent;
    }
}
