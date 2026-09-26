package io.github.opticsz.civmicroscope.sim;

public record CivEvent(
        long id,
        long year,
        Type type,
        Group group,
        Group otherGroup,
        double x,
        double z,
        double magnitude,
        String description
) {
    public enum Type {
        BIRTH,
        DEATH,
        VIOLENCE,
        STOCKPILE_DEPOSIT,
        STOCKPILE_WITHDRAWAL,
        PROGRESSION,
        MIGRATION,
        COOPERATION
    }
}
