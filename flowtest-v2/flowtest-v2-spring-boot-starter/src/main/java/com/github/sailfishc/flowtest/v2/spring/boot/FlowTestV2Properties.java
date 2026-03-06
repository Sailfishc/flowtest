package com.github.sailfishc.flowtest.v2.spring.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for FlowTest V2 Spring Boot integration.
 */
@ConfigurationProperties(prefix = "flowtest.v2")
public class FlowTestV2Properties {

    private boolean enabled = true;
    private String dataFiller = "instancio";
    private final DatasourceProperties datasource = new DatasourceProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDataFiller() {
        return dataFiller;
    }

    public void setDataFiller(String dataFiller) {
        this.dataFiller = dataFiller;
    }

    public DatasourceProperties getDatasource() {
        return datasource;
    }

    public static class DatasourceProperties {

        private String defaultName;
        private List<BindingProperties> bindings = new ArrayList<BindingProperties>();

        public String getDefaultName() {
            return defaultName;
        }

        public void setDefaultName(String defaultName) {
            this.defaultName = defaultName;
        }

        public List<BindingProperties> getBindings() {
            return bindings;
        }

        public void setBindings(List<BindingProperties> bindings) {
            this.bindings = bindings;
        }
    }

    public static class BindingProperties {

        private String name;
        private List<String> tables = new ArrayList<String>();
        private List<String> patterns = new ArrayList<String>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getTables() {
            return tables;
        }

        public void setTables(List<String> tables) {
            this.tables = tables;
        }

        public List<String> getPatterns() {
            return patterns;
        }

        public void setPatterns(List<String> patterns) {
            this.patterns = patterns;
        }
    }
}
