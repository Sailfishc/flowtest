package com.flowtest.core.fixture;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Specification for an entity to be created during the arrange phase.
 *
 * @param <T> the entity type
 */
public class EntitySpec<T> {

    private final Class<T> entityClass;
    private final String alias;
    private final List<Trait<T>> traits;

    @SafeVarargs
    public EntitySpec(Class<T> entityClass, String alias, Trait<T>... traits) {
        this.entityClass = entityClass;
        this.alias = alias;
        this.traits = traits != null ? Arrays.asList(traits) : Collections.emptyList();
    }

    public EntitySpec(Class<T> entityClass, String alias, List<Trait<T>> traits) {
        this.entityClass = entityClass;
        this.alias = alias;
        this.traits = traits != null ? traits : Collections.emptyList();
    }

    public Class<T> getEntityClass() {
        return entityClass;
    }

    public String getAlias() {
        return alias;
    }

    public List<Trait<T>> getTraits() {
        return traits;
    }

    /**
     * Applies all traits to the given entity.
     */
    public void applyTraits(T entity) {
        for (Trait<T> trait : traits) {
            if (trait != null) {
                trait.apply(entity);
            }
        }
    }
}
