package io.github.opticsz.civmicroscope.sim;

public final class SimAgent {
    public Group group;
    public double x;
    public double z;
    public int age;
    public double resources;

    // Heritable, mutable behavioural traits in [0,1].
    public double deathFear;
    public double hoarding;
    public double prejudice;

    public AgentAction action = AgentAction.REST;
    public boolean alive = true;

    public SimAgent(
            Group group,
            double x,
            double z,
            int age,
            double resources,
            double deathFear,
            double hoarding,
            double prejudice
    ) {
        this.group = group;
        this.x = x;
        this.z = z;
        this.age = age;
        this.resources = resources;
        this.deathFear = deathFear;
        this.hoarding = hoarding;
        this.prejudice = prejudice;
    }
}
