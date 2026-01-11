package com.flowtest.mockito;

import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thread-safe container for mock objects within a test.
 * Stores mocks by type and optional alias for retrieval.
 */
public class MockContext {

    private final Map<Class<?>, List<Object>> mocks = new LinkedHashMap<>();
    private final Map<String, Object> aliasedMocks = new LinkedHashMap<>();
    private final Map<String, Class<?>> aliasTypes = new LinkedHashMap<>();

    /**
     * Registers a mock by its type.
     *
     * @param type the class of the mock
     * @param mock the mock instance
     * @param <T>  the type of the mock
     */
    public <T> void registerMock(Class<T> type, T mock) {
        mocks.computeIfAbsent(type, k -> new ArrayList<>()).add(mock);
    }

    /**
     * Registers a mock with an alias.
     *
     * @param alias the alias name
     * @param type  the class of the mock
     * @param mock  the mock instance
     * @param <T>   the type of the mock
     */
    public <T> void registerMock(String alias, Class<T> type, T mock) {
        aliasedMocks.put(alias, mock);
        aliasTypes.put(alias, type);
        registerMock(type, mock);
    }

    /**
     * Gets the first mock of the given type.
     *
     * @param type the class of the mock
     * @param <T>  the type of the mock
     * @return the mock instance
     * @throws IllegalStateException if no mock of this type exists
     */
    @SuppressWarnings("unchecked")
    public <T> T getMock(Class<T> type) {
        List<Object> typeMocks = mocks.get(type);
        if (typeMocks == null || typeMocks.isEmpty()) {
            throw new IllegalStateException("No mock found for type: " + type.getName());
        }
        return (T) typeMocks.get(0);
    }

    /**
     * Gets a mock by index for the given type.
     *
     * @param type  the class of the mock
     * @param index the index of the mock (0-based)
     * @param <T>   the type of the mock
     * @return the mock instance
     * @throws IllegalStateException if no mock exists at this index
     */
    @SuppressWarnings("unchecked")
    public <T> T getMock(Class<T> type, int index) {
        List<Object> typeMocks = mocks.get(type);
        if (typeMocks == null || index >= typeMocks.size()) {
            throw new IllegalStateException("No mock found for type: " + type.getName() + " at index: " + index);
        }
        return (T) typeMocks.get(index);
    }

    /**
     * Gets a mock by its alias.
     *
     * @param alias the alias name
     * @param type  the expected class of the mock
     * @param <T>   the type of the mock
     * @return the mock instance
     * @throws IllegalStateException if no mock exists with this alias
     */
    @SuppressWarnings("unchecked")
    public <T> T getMock(String alias, Class<T> type) {
        Object mock = aliasedMocks.get(alias);
        if (mock == null) {
            throw new IllegalStateException("No mock found with alias: " + alias);
        }
        return (T) mock;
    }

    /**
     * Gets all mocks of the given type.
     *
     * @param type the class of the mock
     * @param <T>  the type of the mock
     * @return list of mock instances, or empty list if none
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getAllMocks(Class<T> type) {
        List<Object> typeMocks = mocks.get(type);
        if (typeMocks == null) {
            return new ArrayList<>();
        }
        List<T> result = new ArrayList<>();
        for (Object mock : typeMocks) {
            result.add((T) mock);
        }
        return result;
    }

    /**
     * Gets all registered mocks.
     *
     * @return list of all mock instances
     */
    public List<Object> getAllMocks() {
        List<Object> all = new ArrayList<>();
        for (List<Object> typeMocks : mocks.values()) {
            all.addAll(typeMocks);
        }
        return all;
    }

    /**
     * Checks if a mock exists for the given type.
     *
     * @param type the class to check
     * @return true if a mock exists
     */
    public boolean hasMock(Class<?> type) {
        List<Object> typeMocks = mocks.get(type);
        return typeMocks != null && !typeMocks.isEmpty();
    }

    /**
     * Checks if a mock exists with the given alias.
     *
     * @param alias the alias to check
     * @return true if a mock exists with this alias
     */
    public boolean hasMock(String alias) {
        return aliasedMocks.containsKey(alias);
    }

    /**
     * Resets all mocks to their initial state using Mockito.reset().
     */
    public void resetAll() {
        for (Object mock : getAllMocks()) {
            Mockito.reset(mock);
        }
    }

    /**
     * Clears all registered mocks.
     */
    public void clear() {
        mocks.clear();
        aliasedMocks.clear();
        aliasTypes.clear();
    }

    /**
     * Gets the number of registered mocks.
     *
     * @return the total count of mocks
     */
    public int size() {
        int count = 0;
        for (List<Object> typeMocks : mocks.values()) {
            count += typeMocks.size();
        }
        return count;
    }
}
