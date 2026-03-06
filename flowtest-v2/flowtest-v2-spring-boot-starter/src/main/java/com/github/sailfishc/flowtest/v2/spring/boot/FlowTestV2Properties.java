package com.github.sailfishc.flowtest.v2.spring.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for FlowTest V2 Spring Boot integration.
 */
@ConfigurationProperties(prefix = "flowtest.v2")
public class FlowTestV2Properties {

    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
