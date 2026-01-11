package com.flowtest.mockito;

/**
 * Functional interface for reusable mock behavior configuration.
 * Similar to the Trait pattern used for entity configuration in FlowTest.
 *
 * <p>Example usage:
 * <pre>{@code
 * public class PaymentServiceMocks {
 *     public static MockTrait<PaymentService> chargeSuccess() {
 *         return config -> config
 *             .when(p -> p.charge(any(), any()))
 *             .thenReturn(PaymentResult.success());
 *     }
 * }
 *
 * // Usage in test:
 * flow.arrange()
 *     .withMocks()
 *         .mock(PaymentService.class, PaymentServiceMocks.chargeSuccess())
 *         .done()
 *     .persist();
 * }</pre>
 *
 * @param <T> the type of the mock being configured
 */
@FunctionalInterface
public interface MockTrait<T> {

    /**
     * Applies this trait's configuration to the mock.
     *
     * @param config the mock configuration to apply behavior to
     */
    void apply(MockConfiguration<T> config);

    /**
     * Combines this trait with another trait.
     *
     * @param other the other trait to combine with
     * @return a new trait that applies both configurations
     */
    default MockTrait<T> and(MockTrait<T> other) {
        return config -> {
            this.apply(config);
            other.apply(config);
        };
    }

    /**
     * Composes multiple traits into a single trait.
     *
     * @param traits the traits to compose
     * @param <T>    the type of the mock
     * @return a new trait that applies all configurations
     */
    @SafeVarargs
    static <T> MockTrait<T> compose(MockTrait<T>... traits) {
        return config -> {
            for (MockTrait<T> trait : traits) {
                trait.apply(config);
            }
        };
    }

    /**
     * Returns a no-op trait that does nothing.
     *
     * @param <T> the type of the mock
     * @return a trait that applies no configuration
     */
    static <T> MockTrait<T> none() {
        return config -> { };
    }
}
