package io.github.opticsz.civmicroscope.sim;

public record CivSnapshot(
        long year,
        int totalPopulation,
        int groupAPopulation,
        int groupBPopulation,
        double groupAResources,
        double groupBResources,
        double groupADeathFear,
        double groupBDeathFear,
        double groupAHoarding,
        double groupBHoarding,
        double groupAPrejudice,
        double groupBPrejudice,
        int groupAViolence,
        int groupBViolence,
        int totalViolentEvents,
        double groupAProgression,
        double groupBProgression,
        double totalProgression,
        double groupAStockpile,
        double groupBStockpile
) {}
