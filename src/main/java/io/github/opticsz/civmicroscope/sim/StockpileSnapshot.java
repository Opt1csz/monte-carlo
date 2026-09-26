package io.github.opticsz.civmicroscope.sim;

public record StockpileSnapshot(
        Group group,
        int cellX,
        int cellZ,
        double amount
) {}
