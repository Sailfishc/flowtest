package com.flowtest.core.fixture;

/**
 * Functional interface for defining entity traits.
 * Traits are composable functions that modify entity properties.
 *
 * <p>Example usage:
 * <pre>{@code
 * public class UserTraits {
 *     public static Trait<User> vip() {
 *         return user -> user.setLevel(UserLevel.VIP);
 *     }
 *
 *     public static Trait<User> balance(double amount) {
 *         return user -> user.setBalance(BigDecimal.valueOf(amount));
 *     }
 * }
 *
 * // Usage in test:
 * flow.arrange()
 *     .add(User.class, UserTraits.vip(), UserTraits.balance(100.00))
 *     .persist();
 * }</pre>
 *
 * @param <T> the entity type
 */
@FunctionalInterface
public interface Trait<T> {

    /**
     * Applies this trait to the given entity.
     *
     * @param entity the entity to modify
     */
    void apply(T entity);

    /**
     * Returns a composed trait that first applies this trait and then applies the other trait.
     *
     * @param other the trait to apply after this one
     * @return a composed trait
     */
    default Trait<T> and(Trait<T> other) {
        return entity -> {
            this.apply(entity);
            other.apply(entity);
        };
    }

    /**
     * Returns a trait that applies all given traits in order.
     *
     * @param traits the traits to compose
     * @param <T> the entity type
     * @return a composed trait
     */
    @SafeVarargs
    static <T> Trait<T> compose(Trait<T>... traits) {
        return entity -> {
            for (Trait<T> trait : traits) {
                if (trait != null) {
                    trait.apply(entity);
                }
            }
        };
    }

    /**
     * Returns a no-op trait that does nothing.
     *
     * @param <T> the entity type
     * @return a no-op trait
     */
    static <T> Trait<T> none() {
        return entity -> {};
    }
}
