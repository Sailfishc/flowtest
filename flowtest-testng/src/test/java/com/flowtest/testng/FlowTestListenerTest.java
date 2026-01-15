package com.flowtest.testng;

import com.flowtest.core.TestFlow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Basic test to validate FlowTestListener works correctly.
 */
@FlowTest
@SpringBootTest(classes = TestApplication.class)
public class FlowTestListenerTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private TestFlow flow;

    @Test
    public void testFlowTestListenerInitializesContext() {
        // Verify TestFlow is autowired
        assertThat(flow).isNotNull();

        // Verify context is initialized by the listener
        assertThat(flow.getContext()).isNotNull();
    }

    @Test
    public void testArrangePhaseWorks() {
        // Verify we can use the arrange phase
        assertThat(flow.arrange()).isNotNull();
    }
}
