package com.github.sailfishc.flowtest.v2.observe.rdbms;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Resolves relational tables to named data sources using exact-table and glob-pattern bindings.
 */
public final class FlowTestDataSourceRegistry {

    private final Map<String, DataSource> dataSources = new LinkedHashMap<String, DataSource>();
    private final Map<String, String> exactBindings = new LinkedHashMap<String, String>();
    private final List<PatternBinding> patternBindings = new ArrayList<PatternBinding>();
    private String defaultDataSourceName;

    public FlowTestDataSourceRegistry register(String name, DataSource dataSource) {
        dataSources.put(requireText(name, "name must not be blank"), requireObject(dataSource, "dataSource must not be null"));
        return this;
    }

    public FlowTestDataSourceRegistry defaultDataSource(String name) {
        this.defaultDataSourceName = requireText(name, "default data source name must not be blank");
        return this;
    }

    public BindingBuilder bind(String name) {
        return new BindingBuilder(this, requireText(name, "data source name must not be blank"));
    }

    public DataSource requireDataSource(String tableName) {
        String name = requireDataSourceName(tableName);
        return requireDataSourceByName(name);
    }

    public DataSource requireDataSourceByName(String name) {
        String resolvedName = requireRegisteredDataSource(name);
        DataSource dataSource = dataSources.get(resolvedName);
        if (dataSource == null) {
            throw new IllegalStateException("No DataSource bean registered for FlowTest data source '" + resolvedName + "'");
        }
        return dataSource;
    }

    public String requireDataSourceName(String tableName) {
        String normalized = normalizeTableName(tableName);
        String exact = exactBindings.get(normalized);
        if (exact != null) {
            return requireRegisteredDataSource(exact);
        }

        Set<String> matched = new LinkedHashSet<String>();
        for (PatternBinding binding : patternBindings) {
            if (binding.matches(normalized)) {
                matched.add(binding.getDataSourceName());
            }
        }
        if (matched.size() == 1) {
            return requireRegisteredDataSource(matched.iterator().next());
        }
        if (matched.size() > 1) {
            throw new IllegalStateException("Multiple FlowTest data source bindings matched table " + tableName + ": " + matched);
        }

        if (defaultDataSourceName != null) {
            return requireRegisteredDataSource(defaultDataSourceName);
        }
        if (dataSources.size() == 1) {
            return dataSources.keySet().iterator().next();
        }
        throw new IllegalStateException("No FlowTest data source binding matched table " + tableName);
    }

    public void validate() {
        requireRegisteredDataSource(defaultDataSourceName, false);
        for (String dataSourceName : exactBindings.values()) {
            requireRegisteredDataSource(dataSourceName);
        }
        for (PatternBinding binding : patternBindings) {
            requireRegisteredDataSource(binding.getDataSourceName());
        }
    }

    private String requireRegisteredDataSource(String name) {
        return requireRegisteredDataSource(name, true);
    }

    private String requireRegisteredDataSource(String name, boolean required) {
        if (name == null) {
            if (required) {
                throw new IllegalStateException("FlowTest data source name must not be null");
            }
            return null;
        }
        if (!dataSources.containsKey(name)) {
            throw new IllegalStateException("No DataSource bean registered for FlowTest data source '" + name + "'");
        }
        return name;
    }

    private void bindExact(String dataSourceName, String tableName) {
        exactBindings.put(normalizeTableName(tableName), dataSourceName);
    }

    private void bindPattern(String dataSourceName, String pattern) {
        patternBindings.add(new PatternBinding(dataSourceName, pattern));
    }

    private static String normalizeTableName(String tableName) {
        return requireText(tableName, "tableName must not be blank").toLowerCase(Locale.ENGLISH);
    }

    private static String requireText(String text, String message) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return text.trim();
    }

    private static <T> T requireObject(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    public static final class BindingBuilder {

        private final FlowTestDataSourceRegistry registry;
        private final String dataSourceName;
        private final List<String> tables = new ArrayList<String>();
        private final List<String> patterns = new ArrayList<String>();

        private BindingBuilder(FlowTestDataSourceRegistry registry, String dataSourceName) {
            this.registry = registry;
            this.dataSourceName = dataSourceName;
        }

        public BindingBuilder table(String tableName) {
            tables.add(requireText(tableName, "tableName must not be blank"));
            return this;
        }

        public BindingBuilder tables(String... tableNames) {
            for (String tableName : tableNames) {
                table(tableName);
            }
            return this;
        }

        public BindingBuilder pattern(String pattern) {
            patterns.add(requireText(pattern, "pattern must not be blank"));
            return this;
        }

        public BindingBuilder patterns(String... values) {
            for (String value : values) {
                pattern(value);
            }
            return this;
        }

        public FlowTestDataSourceRegistry register() {
            for (String table : tables) {
                registry.bindExact(dataSourceName, table);
            }
            for (String pattern : patterns) {
                registry.bindPattern(dataSourceName, pattern);
            }
            return registry;
        }
    }

    private static final class PatternBinding {

        private final String dataSourceName;
        private final String expression;
        private final Pattern compiledPattern;

        private PatternBinding(String dataSourceName, String expression) {
            this.dataSourceName = dataSourceName;
            this.expression = requireText(expression, "pattern must not be blank");
            this.compiledPattern = Pattern.compile(globToRegex(this.expression), Pattern.CASE_INSENSITIVE);
        }

        public String getDataSourceName() {
            return dataSourceName;
        }

        public boolean matches(String tableName) {
            return compiledPattern.matcher(tableName).matches();
        }

        @SuppressWarnings("unused")
        public String getExpression() {
            return expression;
        }

        private static String globToRegex(String glob) {
            StringBuilder regex = new StringBuilder("^");
            for (int i = 0; i < glob.length(); i++) {
                char ch = glob.charAt(i);
                if (ch == '*') {
                    regex.append(".*");
                } else if (ch == '?') {
                    regex.append('.');
                } else if ("\\.[]{}()+-^$|".indexOf(ch) >= 0) {
                    regex.append('\\').append(ch);
                } else {
                    regex.append(ch);
                }
            }
            regex.append('$');
            return regex.toString();
        }
    }
}
