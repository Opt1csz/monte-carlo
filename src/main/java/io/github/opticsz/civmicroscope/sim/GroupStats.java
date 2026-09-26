package io.github.opticsz.civmicroscope.sim;

public final class GroupStats {
    public int population;
    public double meanResources;
    public double meanDeathFear;
    public double meanHoarding;
    public double meanPrejudice;
    public double meanX;
    public double meanZ;
    public double density;

    public int violenceInvolvement;
    public double progression;
    public double stockpileResources;

    public void clear() {
        population = 0;
        meanResources = 0.0;
        meanDeathFear = 0.0;
        meanHoarding = 0.0;
        meanPrejudice = 0.0;
        meanX = 0.5;
        meanZ = 0.5;
        density = 0.0;
        violenceInvolvement = 0;
        progression = 0.0;
        stockpileResources = 0.0;
    }
}
