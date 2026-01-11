package com.flowtest.mockito;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class MockContextTest {

    private MockContext mockContext;

    @BeforeEach
    void setUp() {
        mockContext = new MockContext();
    }

    @Test
    void registerAndGetMock() {
        // Given
        Runnable mock = org.mockito.Mockito.mock(Runnable.class);

        // When
        mockContext.registerMock(Runnable.class, mock);

        // Then
        assertThat(mockContext.getMock(Runnable.class)).isSameAs(mock);
        assertThat(mockContext.hasMock(Runnable.class)).isTrue();
    }

    @Test
    void registerAndGetMockWithAlias() {
        // Given
        Runnable mock = org.mockito.Mockito.mock(Runnable.class);

        // When
        mockContext.registerMock("myRunner", Runnable.class, mock);

        // Then
        assertThat(mockContext.getMock("myRunner", Runnable.class)).isSameAs(mock);
        assertThat(mockContext.hasMock("myRunner")).isTrue();
    }

    @Test
    void getMultipleMocksOfSameType() {
        // Given
        Runnable mock1 = org.mockito.Mockito.mock(Runnable.class);
        Runnable mock2 = org.mockito.Mockito.mock(Runnable.class);

        // When
        mockContext.registerMock(Runnable.class, mock1);
        mockContext.registerMock(Runnable.class, mock2);

        // Then
        assertThat(mockContext.getMock(Runnable.class)).isSameAs(mock1);
        assertThat(mockContext.getMock(Runnable.class, 0)).isSameAs(mock1);
        assertThat(mockContext.getMock(Runnable.class, 1)).isSameAs(mock2);

        List<Runnable> all = mockContext.getAllMocks(Runnable.class);
        assertThat(all).containsExactly(mock1, mock2);
    }
}
