package com.flowtest.mockito;

import com.flowtest.core.TestContext;
import com.flowtest.core.TestFlow;

import java.util.List;

/**
 * Wrapper around TestFlow that adds mock support.
 * Use this class instead of TestFlow when you need mock functionality.
 *
 * <p>Example usage:
 * <pre>{@code
 * @Autowired TestFlow flow;
 *
 * MockTestFlow mockFlow = MockTestFlow.wrap(flow);
 * mockFlow.arrange()
 *     .withMocks()
 *         .mock(PaymentService.class)
 *             .when(p -> p.charge(any(), any()))
 *             .thenReturn(PaymentResult.success())
 *         .done()
 *     .add(User.class, UserTraits.vip())
 *     .persist()
 *     .act(() -> {
 *         PaymentService mock = mockFlow.getMock(PaymentService.class);
 *         return service.process(mock);
 *     })
 *     .assertThat()
 *         .noException()
 *         .mocks()
 *             .verify(PaymentService.class)
 *                 .called(p -> p.charge(any(), any()))
 *             .done();
 * }</pre>
 */
public class MockTestFlow {

    private final TestFlow delegate;

    public MockTestFlow(TestFlow delegate) {
        this.delegate = delegate;
    }

    /**
     * Wraps a TestFlow with mock support.
     */
    public static MockTestFlow wrap(TestFlow flow) {
        return new MockTestFlow(flow);
    }

    /**
     * Starts the Arrange phase with mock support.
     */
    public MockArrangeBuilder arrange() {
        return new MockArrangeBuilder(
            delegate.arrange(),
            delegate.getOrCreateContext()
        );
    }

    /**
     * Gets a mock of the given type.
     */
    public <T> T getMock(Class<T> mockClass) {
        MockContext mockContext = getMockContext();
        return mockContext.getMock(mockClass);
    }

    /**
     * Gets a mock by alias.
     */
    public <T> T getMock(String alias, Class<T> mockClass) {
        MockContext mockContext = getMockContext();
        return mockContext.getMock(alias, mockClass);
    }

    /**
     * Gets all mocks of the given type.
     */
    public <T> List<T> getAllMocks(Class<T> mockClass) {
        MockContext mockContext = getMockContext();
        return mockContext.getAllMocks(mockClass);
    }

    private MockContext getMockContext() {
        TestContext context = delegate.getContext();
        Object mockCtx = context.getMockContext();
        if (mockCtx == null) {
            throw new IllegalStateException(
                "No mocks configured. Use withMocks() in arrange phase first.");
        }
        return (MockContext) mockCtx;
    }

    // Delegate methods for entity access

    public <T> T get(Class<T> entityClass) {
        return delegate.get(entityClass);
    }

    public <T> T get(String alias, Class<T> entityClass) {
        return delegate.get(alias, entityClass);
    }

    public <T> List<T> getAll(Class<T> entityClass) {
        return delegate.getAll(entityClass);
    }

    public <T> T get(Class<T> entityClass, int index) {
        return delegate.get(entityClass, index);
    }

    public TestContext getContext() {
        return delegate.getContext();
    }

    public TestFlow getDelegate() {
        return delegate;
    }
}
