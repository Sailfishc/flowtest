package com.github.sailfishc.flowtest.v2.runtime;

import java.util.function.Consumer;

/**
 * Optional observation configuration for resources that cannot be auto-inferred from {@code then(...)}.
 * Used for route conditions, dynamic table parameters, and inspect-only resources.
 *
 * <pre>{@code
 * .observe(o -> {
 *     o.table("ft_order", r -> r.route("tenant_id", 100L));
 *     o.table("ft_order_dynamic", r -> r.dynamicTableBy("bucket", "a"));
 * })
 * }</pre>
 */
public interface ObserveSpec {

    ObserveSpec table(String tableName);

    ObserveSpec table(String tableName, Consumer<ObservedResourceSpec> spec);

    ObserveSpec entity(Class<?> entityType);

    ObserveSpec entity(Class<?> entityType, Consumer<ObservedResourceSpec> spec);
}
