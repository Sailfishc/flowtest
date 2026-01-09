package com.flowtest.spring.boot;

import com.flowtest.core.lifecycle.CleanupMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for FlowTest framework.
 *
 * <p>Example application.yml:
 * <pre>{@code
 * flowtest:
 *   cleanup-mode: TRANSACTION
 *   seed: 12345
 *   string-length-min: 5
 *   string-length-max: 20
 *   snapshot-tables:
 *     - t_order
 *     - t_user
 * }</pre>
 */
@ConfigurationProperties(prefix = "flowtest")
public class FlowTestProperties {

    /**
     * Default cleanup mode for tests.
     */
    private CleanupMode cleanupMode = CleanupMode.TRANSACTION;

    /**
     * Random seed for auto-filling (0 = use current time).
     */
    private long seed = 0;

    /**
     * Minimum string length for auto-generated strings.
     */
    private int stringLengthMin = 5;

    /**
     * Maximum string length for auto-generated strings.
     */
    private int stringLengthMax = 20;

    /**
     * Minimum collection size for auto-generated collections.
     */
    private int collectionSizeMin = 1;

    /**
     * Maximum collection size for auto-generated collections.
     */
    private int collectionSizeMax = 3;

    /**
     * Maximum randomization depth for nested objects.
     */
    private int randomizationDepth = 3;

    /**
     * Tables to monitor for snapshot assertions by default.
     */
    private List<String> snapshotTables = new ArrayList<>();

    /**
     * Name of the ID column for snapshot tracking.
     */
    private String idColumnName = "id";

    // Getters and Setters

    public CleanupMode getCleanupMode() {
        return cleanupMode;
    }

    public void setCleanupMode(CleanupMode cleanupMode) {
        this.cleanupMode = cleanupMode;
    }

    public long getSeed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public int getStringLengthMin() {
        return stringLengthMin;
    }

    public void setStringLengthMin(int stringLengthMin) {
        this.stringLengthMin = stringLengthMin;
    }

    public int getStringLengthMax() {
        return stringLengthMax;
    }

    public void setStringLengthMax(int stringLengthMax) {
        this.stringLengthMax = stringLengthMax;
    }

    public int getCollectionSizeMin() {
        return collectionSizeMin;
    }

    public void setCollectionSizeMin(int collectionSizeMin) {
        this.collectionSizeMin = collectionSizeMin;
    }

    public int getCollectionSizeMax() {
        return collectionSizeMax;
    }

    public void setCollectionSizeMax(int collectionSizeMax) {
        this.collectionSizeMax = collectionSizeMax;
    }

    public int getRandomizationDepth() {
        return randomizationDepth;
    }

    public void setRandomizationDepth(int randomizationDepth) {
        this.randomizationDepth = randomizationDepth;
    }

    public List<String> getSnapshotTables() {
        return snapshotTables;
    }

    public void setSnapshotTables(List<String> snapshotTables) {
        this.snapshotTables = snapshotTables;
    }

    public String getIdColumnName() {
        return idColumnName;
    }

    public void setIdColumnName(String idColumnName) {
        this.idColumnName = idColumnName;
    }
}
