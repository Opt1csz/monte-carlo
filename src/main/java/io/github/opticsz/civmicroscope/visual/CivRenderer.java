package io.github.opticsz.civmicroscope.visual;

import io.github.opticsz.civmicroscope.CivMicroscopePlugin;
import io.github.opticsz.civmicroscope.sim.AgentAction;
import io.github.opticsz.civmicroscope.sim.CivEvent;
import io.github.opticsz.civmicroscope.sim.CivSimulation;
import io.github.opticsz.civmicroscope.sim.CivSnapshot;
import io.github.opticsz.civmicroscope.sim.Group;
import io.github.opticsz.civmicroscope.sim.GroupStats;
import io.github.opticsz.civmicroscope.sim.SimAgent;
import io.github.opticsz.civmicroscope.sim.StockpileSnapshot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class CivRenderer {
    private final CivMicroscopePlugin plugin;
    private final CivSimulation simulation;
    private final World world;
    private final int centerX;
    private final int centerZ;
    private final double viewRadius;

    private final int personsPerVillager;
    private final int maxVillagersPerGroup;
    private final int updateEveryTicks;
    private final int retargetEveryTicks;
    private final double followStrength;
    private final double movementNoise;
    private final double verticalOffset;
    private final boolean showNames;
    private final boolean glowing;
    private final boolean showStockpileLabels;

    private final EnumMap<Group, Team> groupTeams;
    private final EnumMap<Group, List<VillagerProxy>> proxies =
            new EnumMap<>(Group.class);
    private final Map<String, StockpileVisual> stockpileVisuals = new HashMap<>();
    private final Random random = new Random();
    private final EnumMap<Group, Integer> nextSerial = new EnumMap<>(Group.class);

    private final List<CivEvent> recentEvents = new ArrayList<>();
    private CivEvent lastFollowedEvent = null;
    private boolean followEvents = true;
    private long tick = 0;
    private long lastSimulationYear = -1;

    public CivRenderer(
            CivMicroscopePlugin plugin,
            CivSimulation simulation,
            World world,
            int centerX,
            int centerZ,
            double viewRadius,
            int personsPerVillager,
            int maxVillagersPerGroup,
            int updateEveryTicks,
            int retargetEveryTicks,
            double followStrength,
            double movementNoise,
            double verticalOffset,
            boolean showNames,
            boolean glowing,
            boolean showStockpileLabels,
            EnumMap<Group, Team> groupTeams
    ) {
        this.plugin = plugin;
        this.simulation = simulation;
        this.world = world;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.viewRadius = viewRadius;
        this.personsPerVillager = personsPerVillager;
        this.maxVillagersPerGroup = maxVillagersPerGroup;
        this.updateEveryTicks = Math.max(1, updateEveryTicks);
        this.retargetEveryTicks = Math.max(1, retargetEveryTicks);
        this.followStrength = followStrength;
        this.movementNoise = movementNoise;
        this.verticalOffset = verticalOffset;
        this.showNames = showNames;
        this.glowing = glowing;
        this.showStockpileLabels = showStockpileLabels;
        this.groupTeams = groupTeams;

        proxies.put(Group.A, new ArrayList<>());
        proxies.put(Group.B, new ArrayList<>());
        nextSerial.put(Group.A, 1);
        nextSerial.put(Group.B, 1);

        adoptExistingProxies();
    }

    public void tick() {
        tick++;

        consumeSimulationEvents();
        if (tick % updateEveryTicks != 0) return;

        syncPopulationVisualisation();
        syncStockpiles();

        if (tick % retargetEveryTicks == 0) {
            retargetAll();
        }

        for (List<VillagerProxy> groupProxies : proxies.values()) {
            for (VillagerProxy proxy : groupProxies) {
                proxy.tick(followStrength, movementNoise, showNames);
                maybeShowStockpileContribution(proxy);
            }
        }

        renderRecentEventParticles();

        if (tick % 20 == 0) {
            renderDensitySignals();
        }
    }

    private void consumeSimulationEvents() {
        List<CivEvent> incoming = simulation.drainEvents();
        if (incoming.isEmpty()) return;
        recentEvents.addAll(incoming);
        while (recentEvents.size() > 40) recentEvents.remove(0);

        if (followEvents) {
            CivEvent important = incoming.stream()
                    .filter(this::followable)
                    .max((a, b) -> Double.compare(eventScore(a), eventScore(b)))
                    .orElse(null);

            if (important != null && !sameEvent(important, lastFollowedEvent)) {
                followEvent(important);
                lastFollowedEvent = important;
            }
        }
    }

    private boolean followable(CivEvent e) {
        return switch (e.type()) {
            case VIOLENCE -> true;
            case STOCKPILE_DEPOSIT, STOCKPILE_WITHDRAWAL -> e.magnitude() >= 1.0;
            case PROGRESSION -> e.magnitude() >= 1.0;
            case MIGRATION -> e.magnitude() >= 0.12;
            case BIRTH -> true;
            default -> false;
        };
    }

    private double eventScore(CivEvent e) {
        double base = switch (e.type()) {
            case VIOLENCE -> 100.0;
            case STOCKPILE_DEPOSIT -> 70.0;
            case STOCKPILE_WITHDRAWAL -> 55.0;
            case PROGRESSION -> 50.0;
            case MIGRATION -> 40.0;
            case BIRTH -> 10.0;
            case DEATH -> 5.0;
            case COOPERATION -> 2.0;
        };
        return base + 10.0 * e.magnitude();
    }

    private boolean sameEvent(CivEvent a, CivEvent b) {
        return b != null && a.id() == b.id();
    }

    private void syncPopulationVisualisation() {
        for (Group group : Group.values()) {
            GroupStats stats = simulation.stats(group);
            int desired = Math.min(
                    maxVillagersPerGroup,
                    Math.max(
                            1,
                            (int) Math.ceil(
                                    stats.population / (double) personsPerVillager
                            )
                    )
            );

            List<VillagerProxy> list = proxies.get(group);
            while (list.size() < desired) {
                list.add(createProxy(group));
            }
            // Deliberately never shrink the list. Visual villagers are persistent.
        }
    }

    private void adoptExistingProxies() {
        for (org.bukkit.entity.Entity entity : world.getEntitiesByClass(org.bukkit.entity.Villager.class)) {
            org.bukkit.entity.Villager villager = (org.bukkit.entity.Villager) entity;
            var pdc = villager.getPersistentDataContainer();
            String groupName = pdc.get(plugin.groupKey(), org.bukkit.persistence.PersistentDataType.STRING);
            Integer serial = pdc.get(plugin.proxyKey(), org.bukkit.persistence.PersistentDataType.INTEGER);
            if (groupName == null || serial == null) continue;

            Group group;
            try {
                group = Group.valueOf(groupName);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            VillagerProxy proxy = new VillagerProxy(
                    group, serial, plugin.proxyKey(), plugin.groupKey(), random
            );
            proxy.adopt(villager);
            proxies.get(group).add(proxy);
            nextSerial.put(group, Math.max(nextSerial.get(group), serial + 1));
            addToTeam(proxy, group);
        }
    }

    private VillagerProxy createProxy(Group group) {
        List<VillagerProxy> list = proxies.get(group);
        int serial = nextSerial.get(group);
        nextSerial.put(group, serial + 1);

        VillagerProxy proxy = new VillagerProxy(
                group,
                serial,
                plugin.proxyKey(),
                plugin.groupKey(),
                random
        );

        SimAgent sample = simulation.randomAgent(group);
        Location target = targetForSample(sample);
        if (target == null) {
            target = safeCenter();
        }

        proxy.spawn(target, glowing, showNames);
        if (sample != null) {
            proxy.setSample(sample, actionTarget(sample), movementNoise, showNames);
            addToTeam(proxy, group);
        }
        return proxy;
    }

    private void addToTeam(VillagerProxy proxy, Group group) {
        Team team = groupTeams.get(group);
        if (team != null && proxy.entity() != null) {
            team.addEntry(proxy.entity().getUniqueId().toString());
        }
    }

    private void retargetAll() {
        for (Group group : Group.values()) {
            List<VillagerProxy> list = proxies.get(group);
            if (list.isEmpty()) continue;

            List<SimAgent> samples = simulation.sampleAgents(group, list.size());
            for (int i = 0; i < list.size(); i++) {
                SimAgent sample = samples.isEmpty()
                        ? simulation.randomAgent(group)
                        : samples.get(i % samples.size());

                if (sample == null) {
                    continue; // extinct demographic: proxies remain where they were
                }

                Location target = actionTarget(sample);
                list.get(i).setSample(
                        sample,
                        target,
                        movementNoise,
                        showNames
                );
            }
        }
    }

    private Location actionTarget(SimAgent sample) {
        if (sample == null) return safeCenter();

        // Make visual action follow the underlying simulation state.
        if (sample.action == AgentAction.HOARD
                || sample.action == AgentAction.GATHER) {
            StockpileSnapshot pile = simulation.nearestStockpile(
                    sample.group, sample.x, sample.z, 0.50
            );
            if (pile != null) return mapStockpile(pile);
        }

        if (sample.action == AgentAction.TRADE
                || sample.action == AgentAction.FIGHT
                || sample.action == AgentAction.FLEE) {
            Group other = sample.group == Group.A ? Group.B : Group.A;
            SimAgent target = simulation.randomAgent(other);
            if (target != null) return mapAgent(target);
        }

        return mapAgent(sample);
    }

    private Location targetForSample(SimAgent sample) {
        return sample == null ? safeCenter() : mapAgent(sample);
    }

    private void syncStockpiles() {
        Map<String, StockpileSnapshot> desired = new HashMap<>();
        for (StockpileSnapshot snapshot : simulation.stockpiles()) {
            String key = stockpileKey(snapshot);
            desired.put(key, snapshot);

            StockpileVisual visual = stockpileVisuals.computeIfAbsent(
                    key,
                    ignored -> new StockpileVisual(
                            plugin,
                            snapshot.group(),
                            snapshot.cellX(),
                            snapshot.cellZ()
                    )
            );
            visual.update(
                    snapshot,
                    mapStockpile(snapshot),
                    showStockpileLabels
            );
        }

        stockpileVisuals.entrySet().removeIf(entry -> {
            if (desired.containsKey(entry.getKey())) return false;
            entry.getValue().remove();
            return true;
        });
    }

    private void renderDensitySignals() {
        CivSimulation.SimSnapshotData data = simulation.dataForViewer();
        int cells = data.spatialCells();
        Map<String, Integer> counts = new HashMap<>();

        for (SimAgent agent : data.agents()) {
            int cx = Math.max(0, Math.min(cells - 1, (int) Math.floor(agent.x * cells)));
            int cz = Math.max(0, Math.min(cells - 1, (int) Math.floor(agent.z * cells)));
            String key = agent.group.name() + ':' + cx + ':' + cz;
            counts.merge(key, 1, Integer::sum);
        }

        counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(8)
                .forEach(entry -> {
                    String[] bits = entry.getKey().split(":");
                    Group group = Group.valueOf(bits[0]);
                    int cx = Integer.parseInt(bits[1]);
                    int cz = Integer.parseInt(bits[2]);
                    Location at = mapNormalized(
                            (cx + 0.5) / cells,
                            (cz + 0.5) / cells
                    );
                    Particle.DustOptions dust = new Particle.DustOptions(
                            group == Group.A ? Color.RED : Color.AQUA,
                            Math.min(2.2F, 0.7F + entry.getValue() / 20.0F)
                    );
                    world.spawnParticle(
                            Particle.DUST,
                            at.clone().add(0, 0.15, 0),
                            Math.min(10, 2 + entry.getValue() / 10),
                            0.7, 0.08, 0.7, 0, dust
                    );
                });
    }

    private void maybeShowStockpileContribution(VillagerProxy proxy) {
        if (proxy.target() == null) return;
        if (proxy.action() != AgentAction.HOARD
                && proxy.action() != AgentAction.GATHER) return;

        double distance = proxy.entity().getLocation().distance(proxy.target());
        if (distance < 2.2 && random.nextDouble() < 0.20) {
            Particle.DustOptions dust = new Particle.DustOptions(
                    proxy.group() == Group.A ? Color.RED : Color.AQUA,
                    1.0F
            );
            proxy.entity().getWorld().spawnParticle(
                    Particle.DUST,
                    proxy.entity().getLocation().add(0, 0.8, 0),
                    3, 0.25, 0.2, 0.25, 0, dust
            );
            proxy.entity().getWorld().spawnParticle(
                    Particle.HAPPY_VILLAGER,
                    proxy.target().clone().add(0, 0.7, 0),
                    2, 0.25, 0.15, 0.25, 0
            );
        }
    }

    private void renderRecentEventParticles() {
        long currentYear = simulation.year();
        for (CivEvent event : recentEvents) {
            if (currentYear - event.year() > 2) continue;
            Location at = mapNormalized(event.x(), event.z());
            if (at == null) continue;

            switch (event.type()) {
                case VIOLENCE -> {
                    world.spawnParticle(
                            Particle.CRIT,
                            at.clone().add(0, 1.2, 0),
                            5, 0.4, 0.4, 0.4, 0
                    );
                    world.spawnParticle(
                            Particle.ANGRY_VILLAGER,
                            at.clone().add(0, 1.0, 0),
                            2, 0.25, 0.25, 0.25, 0
                    );
                }
                case BIRTH -> world.spawnParticle(
                        Particle.HEART,
                        at.clone().add(0, 1.2, 0),
                        3, 0.25, 0.25, 0.25, 0
                );
                case DEATH -> world.spawnParticle(
                        Particle.SOUL,
                        at.clone().add(0, 0.8, 0),
                        3, 0.20, 0.20, 0.20, 0
                );
                case STOCKPILE_DEPOSIT, STOCKPILE_WITHDRAWAL -> world.spawnParticle(
                        Particle.COMPOSTER,
                        at.clone().add(0, 0.8, 0),
                        4, 0.25, 0.20, 0.25, 0
                );
                case PROGRESSION -> world.spawnParticle(
                        Particle.ENCHANT,
                        at.clone().add(0, 1.2, 0),
                        8, 0.3, 0.5, 0.3, 0.2
                );
                case MIGRATION -> world.spawnParticle(
                        Particle.CLOUD,
                        at.clone().add(0, 0.7, 0),
                        4, 0.3, 0.25, 0.3, 0
                );
                case COOPERATION -> world.spawnParticle(
                        Particle.HAPPY_VILLAGER,
                        at.clone().add(0, 1.1, 0),
                        3, 0.25, 0.2, 0.25, 0
                );
            }
        }
    }

    public void followEvent(CivEvent event) {
        if (event == null) return;
        Location focus = mapNormalized(event.x(), event.z());
        if (focus == null) return;

        for (Player player : world.getPlayers()) {
            Location camera = focus.clone().add(0, 16, 18);
            camera.setYaw((float) Math.toDegrees(Math.atan2(
                    focus.getX() - camera.getX(),
                    focus.getZ() - camera.getZ()
            )));
            camera.setPitch((float) -Math.toDegrees(Math.atan2(
                    focus.getY() - camera.getY(),
                    Math.hypot(
                            focus.getX() - camera.getX(),
                            focus.getZ() - camera.getZ()
                    )
            )));
            player.teleport(camera);
        }
    }

    public void teleportPlayersToView() {
        Location center = safeCenter();
        for (Player player : world.getPlayers()) {
            Location camera = center.clone().add(0, 20, 25);
            camera.setPitch(35F);
            camera.setYaw(180F);
            player.teleport(camera);
        }
    }

    public void toggleFollow() {
        followEvents = !followEvents;
    }

    public void setFollow(boolean enabled) {
        followEvents = enabled;
    }

    public boolean isFollowing() { return followEvents; }

    public void jumpToLatest() {
        recentEvents.stream()
                .filter(this::followable)
                .max((a, b) -> Long.compare(a.id(), b.id()))
                .ifPresent(this::followEvent);
    }

    public void sendHud() {
        CivSnapshot s = simulation.snapshot();
        Component hud = Component.text(
                String.format(
                        "Year %d | A %d  R %.1f  X %.2f  V %d  P %.0f | "
                                + "B %d  R %.1f  X %.2f  V %d  P %.0f | "
                                + "Violence %d | Stockpiles A %.0f B %.0f | %s",
                        s.year(),
                        s.groupAPopulation(), s.groupAResources(), s.groupAPrejudice(),
                        s.groupAViolence(), s.groupAProgression(),
                        s.groupBPopulation(), s.groupBResources(), s.groupBPrejudice(),
                        s.groupBViolence(), s.groupBProgression(),
                        s.totalViolentEvents(),
                        s.groupAStockpile(), s.groupBStockpile(),
                        followEvents ? "FOLLOW ON" : "FOLLOW OFF"
                ),
                NamedTextColor.WHITE
        );

        for (Player player : world.getPlayers()) {
            player.sendActionBar(hud);
        }
    }

    private Location mapAgent(SimAgent agent) {
        return agent == null ? null : mapNormalized(agent.x, agent.z);
    }

    private Location mapStockpile(StockpileSnapshot snapshot) {
        if (snapshot == null) return null;
        double x = simulation.cellCenter(snapshot.cellX());
        double z = simulation.cellCenter(snapshot.cellZ());
        return mapNormalized(x, z);
    }

    private Location mapNormalized(double x, double z) {
        int worldX = centerX + (int) Math.round((2.0 * x - 1.0) * viewRadius);
        int worldZ = centerZ + (int) Math.round((2.0 * z - 1.0) * viewRadius);
        int y = world.getHighestBlockYAt(worldX, worldZ) + 1;
        return new Location(world, worldX + 0.5, y + verticalOffset, worldZ + 0.5);
    }

    private Location safeCenter() {
        int y = world.getHighestBlockYAt(centerX, centerZ) + 1;
        return new Location(world, centerX + 0.5, y + verticalOffset, centerZ + 0.5);
    }

    private String stockpileKey(StockpileSnapshot s) {
        return s.group().name() + ":" + s.cellX() + ":" + s.cellZ();
    }
}
