package io.github.opticsz.civmicroscope.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class CivSimulationTest {

    private CivSimulation make() {
        return new CivSimulation(
                100, 1000, 0.30, 42L,
                50.0, 2.0, 1.0,
                0.70, 0.60, 0.55,
                0.75, 0.06,
                0.045, 0.050, 0.020, 0.080, 0.00035, 16,
                0.03, 0.08, 0.20,
                0.20, 0.004, 0.10
        );
    }

    @Test
    void startsWithTwoDemographics() {
        CivSimulation sim = make();
        assertTrue(sim.snapshot().groupAPopulation() > 0);
        assertTrue(sim.snapshot().groupBPopulation() > 0);
    }

    @Test
    void populationCanChange() {
        CivSimulation sim = make();
        int start = sim.totalPopulation();

        for (int i = 0; i < 25; i++) {
            sim.step();
        }

        assertNotEquals(start, sim.totalPopulation());
    }

    @Test
    void traitsRemainBounded() {
        CivSimulation sim = make();

        for (int i = 0; i < 25; i++) {
            sim.step();
        }

        assertTrue(sim.stats(Group.A).meanDeathFear >= 0);
        assertTrue(sim.stats(Group.A).meanDeathFear <= 1);
        assertTrue(sim.stats(Group.B).meanHoarding >= 0);
        assertTrue(sim.stats(Group.B).meanHoarding <= 1);
    }

    @Test
    void violenceAndProgressionAreTrackedSeparately() {
        CivSimulation sim = make();

        for (int i = 0; i < 25; i++) {
            sim.step();
        }

        assertTrue(sim.violentEvents() >= 0);
        assertTrue(sim.progressionFor(Group.A) >= 0);
        assertTrue(sim.progressionFor(Group.B) >= 0);
    }
}
