package io.github.opticsz.civmicroscope;

import io.github.opticsz.civmicroscope.sim.CivEvent;
import io.github.opticsz.civmicroscope.sim.CivSimulation;
import io.github.opticsz.civmicroscope.sim.CivSnapshot;
import io.github.opticsz.civmicroscope.sim.SimAgent;
import io.github.opticsz.civmicroscope.sim.StockpileSnapshot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Writes the live numerical state for the optional external 3-D viewer. */
public final class LiveStateExporter {
    private final Path stateDir;
    private int currentSpatialCells = 16;

    public LiveStateExporter(Path pluginDataFolder) {
        this.stateDir = pluginDataFolder.resolve("state");
    }

    public Path stateDir() { return stateDir; }

    public void export(CivSimulation.SimSnapshotData data) {
        currentSpatialCells = data.spatialCells();
        try {
            Files.createDirectories(stateDir);
            atomicWrite("stats.csv", statsCsv(data.snapshot()));
            atomicWrite("agents.csv", agentsCsv(data.agents()));
            atomicWrite("stockpiles.csv", stockpilesCsv(data.stockpiles()));
            atomicWrite("events.csv", eventsCsv(data.events()));
            atomicWrite("READY", "year=" + data.year() + "\n");
        } catch (IOException e) {
            throw new IllegalStateException("Could not export live simulation state", e);
        }
    }

    private void atomicWrite(String name, String text) throws IOException {
        Path target = stateDir.resolve(name);
        Path temp = stateDir.resolve(name + ".tmp");
        Files.writeString(temp, text, StandardCharsets.UTF_8);
        try {
            Files.move(
                    temp,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String statsCsv(CivSnapshot s) {
        return "year,total_population,groupA_population,groupB_population," +
                "groupA_resources,groupB_resources,groupA_death_fear,groupB_death_fear," +
                "groupA_hoarding,groupB_hoarding,groupA_prejudice,groupB_prejudice," +
                "groupA_violence,groupB_violence,total_violent_events," +
                "groupA_progression,groupB_progression,total_progression," +
                "groupA_stockpile,groupB_stockpile\n" +
                String.format(
                        java.util.Locale.ROOT,
                        "%d,%d,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f\n",
                        s.year(), currentSpatialCells, s.totalPopulation(), s.groupAPopulation(), s.groupBPopulation(),
                        s.groupAResources(), s.groupBResources(), s.groupADeathFear(), s.groupBDeathFear(),
                        s.groupAHoarding(), s.groupBHoarding(), s.groupAPrejudice(), s.groupBPrejudice(),
                        s.groupAViolence(), s.groupBViolence(), s.totalViolentEvents(),
                        s.groupAProgression(), s.groupBProgression(), s.totalProgression(),
                        s.groupAStockpile(), s.groupBStockpile()
                );
    }

    private String agentsCsv(List<SimAgent> agents) {
        StringBuilder sb = new StringBuilder();
        sb.append("group,x,z,age,resources,death_fear,hoarding,prejudice,action\n");
        for (SimAgent a : agents) {
            sb.append(String.format(
                    java.util.Locale.ROOT,
                    "%s,%.6f,%.6f,%d,%.6f,%.6f,%.6f,%.6f,%s\n",
                    a.group.name(), a.x, a.z, a.age, a.resources,
                    a.deathFear, a.hoarding, a.prejudice, a.action.name()
            ));
        }
        return sb.toString();
    }

    private String stockpilesCsv(List<StockpileSnapshot> stockpiles) {
        StringBuilder sb = new StringBuilder();
        sb.append("group,cell_x,cell_z,amount\n");
        for (StockpileSnapshot s : stockpiles) {
            sb.append(String.format(
                    java.util.Locale.ROOT,
                    "%s,%d,%d,%.6f\n",
                    s.group().name(), s.cellX(), s.cellZ(), s.amount()
            ));
        }
        return sb.toString();
    }

    private String eventsCsv(List<CivEvent> events) {
        StringBuilder sb = new StringBuilder();
        sb.append("id,year,type,group,other_group,x,z,magnitude,description\n");
        for (CivEvent e : events) {
            sb.append(String.format(
                    java.util.Locale.ROOT,
                    "%d,%d,%s,%s,%s,%.6f,%.6f,%.6f,%s\n",
                    e.id(), e.year(), e.type().name(),
                    e.group() == null ? "" : e.group().name(),
                    e.otherGroup() == null ? "" : e.otherGroup().name(),
                    e.x(), e.z(), e.magnitude(),
                    e.description().replace(',', ';')
            ));
        }
        return sb.toString();
    }
}
