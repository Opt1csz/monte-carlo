package io.github.opticsz.civmicroscope.sim;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Model 1 reference simulation.
 *
 * The simulation contains explicit agents for now. Minecraft only sees
 * samples/proxies of this state. The eventual distributional engine can
 * replace this class behind the same renderer-facing queries.
 */
public final class CivSimulation {
    private final SplittableRandom rng;
    private final ArrayList<SimAgent> agents = new ArrayList<>();
    private final EnumMap<Group, GroupStats> stats = new EnumMap<>(Group.class);
    private final HashMap<StockpileKey, Double> stockpiles = new HashMap<>();
    private final ArrayDeque<CivEvent> events = new ArrayDeque<>();

    private long nextEventId = 1;
    private long year = 0;

    private final int maxPopulation;
    private final int spatialCells;
    private final double initialResourcePerAgent;
    private final double productionPerWorker;
    private final double consumptionPerYear;
    private final double baseWorkProbability;
    private final double interactionRate;
    private final double baseCooperation;

    private final double inheritanceStrength;
    private final double mutationSd;
    private final double traitLearningRate;

    private final double birthRate;
    private final double resourceBirthBonus;
    private final double deathRate;
    private final double scarcityDeathPenalty;
    private final double ageDeathScale;
    private final int reproductiveAge;

    private final double violenceBase;
    private final double violenceScarcity;
    private final double violencePrejudice;
    private final double cooperationPrejudiceReduction;
    private final double contactPrejudiceReduction;
    private final double violencePrejudiceIncrease;

    private final double progressionBase;
    private final double progressionResourceScale;
    private final double progressionDiversityBonus;

    private final double stockpileDepositFraction;
    private final double stockpileWithdrawFraction;

    private double groupAProgression = 0.0;
    private double groupBProgression = 0.0;
    private int groupAViolence = 0;
    private int groupBViolence = 0;
    private int totalViolentEvents = 0;

    public CivSimulation(
            int initialPopulation,
            int maxPopulation,
            double initialPrejudice,
            long seed,
            int spatialCells,
            double initialResourcePerAgent,
            double productionPerWorker,
            double consumptionPerYear,
            double baseWorkProbability,
            double interactionRate,
            double baseCooperation,
            double inheritanceStrength,
            double mutationSd,
            double traitLearningRate,
            double birthRate,
            double resourceBirthBonus,
            double deathRate,
            double scarcityDeathPenalty,
            double ageDeathScale,
            int reproductiveAge,
            double violenceBase,
            double violenceScarcity,
            double violencePrejudice,
            double cooperationPrejudiceReduction,
            double contactPrejudiceReduction,
            double violencePrejudiceIncrease,
            double progressionBase,
            double progressionResourceScale,
            double progressionDiversityBonus,
            double stockpileDepositFraction,
            double stockpileWithdrawFraction
    ) {
        this.rng = new SplittableRandom(seed);
        this.maxPopulation = maxPopulation;
        this.spatialCells = Math.max(2, spatialCells);
        this.initialResourcePerAgent = initialResourcePerAgent;
        this.productionPerWorker = productionPerWorker;
        this.consumptionPerYear = consumptionPerYear;
        this.baseWorkProbability = baseWorkProbability;
        this.interactionRate = interactionRate;
        this.baseCooperation = baseCooperation;

        this.inheritanceStrength = clamp01(inheritanceStrength);
        this.mutationSd = Math.max(0.0, mutationSd);
        this.traitLearningRate = clamp01(traitLearningRate);

        this.birthRate = Math.max(0.0, birthRate);
        this.resourceBirthBonus = Math.max(0.0, resourceBirthBonus);
        this.deathRate = Math.max(0.0, deathRate);
        this.scarcityDeathPenalty = Math.max(0.0, scarcityDeathPenalty);
        this.ageDeathScale = Math.max(0.0, ageDeathScale);
        this.reproductiveAge = Math.max(0, reproductiveAge);

        this.violenceBase = Math.max(0.0, violenceBase);
        this.violenceScarcity = Math.max(0.0, violenceScarcity);
        this.violencePrejudice = Math.max(0.0, violencePrejudice);
        this.cooperationPrejudiceReduction = clamp01(cooperationPrejudiceReduction);
        this.contactPrejudiceReduction = clamp01(contactPrejudiceReduction);
        this.violencePrejudiceIncrease = clamp01(violencePrejudiceIncrease);

        this.progressionBase = Math.max(0.0, progressionBase);
        this.progressionResourceScale = Math.max(0.0, progressionResourceScale);
        this.progressionDiversityBonus = Math.max(0.0, progressionDiversityBonus);

        this.stockpileDepositFraction = clamp01(stockpileDepositFraction);
        this.stockpileWithdrawFraction = clamp01(stockpileWithdrawFraction);

        stats.put(Group.A, new GroupStats());
        stats.put(Group.B, new GroupStats());

        initialize(initialPopulation, clamp01(initialPrejudice));
        recomputeStats();
    }

    private void initialize(int initialPopulation, double initialPrejudice) {
        int a = initialPopulation / 2;
        int b = initialPopulation - a;
        for (int i = 0; i < a; i++) agents.add(randomInitialAgent(Group.A, initialPrejudice));
        for (int i = 0; i < b; i++) agents.add(randomInitialAgent(Group.B, initialPrejudice));
    }

    private SimAgent randomInitialAgent(Group group, double initialPrejudice) {
        return new SimAgent(
                group,
                rng.nextDouble(), rng.nextDouble(),
                rng.nextInt(18, 55),
                initialResourcePerAgent,
                clamp01(gaussian(0.50, 0.18)),
                clamp01(gaussian(0.50, 0.18)),
                clamp01(gaussian(initialPrejudice, 0.10))
        );
    }

    public synchronized void step() {
        if (agents.isEmpty()) {
            year++;
            recomputeStats();
            return;
        }

        // This transition represents the next simulated year.
        year++;

        // Choose actions from the current state before resource/stockpile and
        // interaction phases so the visible action and actual model agree.
        actionPhase();
        resourcePhase();
        interactionPhase();
        progressionPhase();
        movementPhase();
        birthPhase();
        deathPhase();
        recomputeStats();
    }

    private void resourcePhase() {
        for (SimAgent a : agents) {
            if (!a.alive) continue;

            double workProbability = clamp01(
                    baseWorkProbability
                            - 0.20 * a.deathFear
                            + 0.20 * a.hoarding
            );

            if (a.action == AgentAction.WORK || rng.nextDouble() < workProbability) {
                a.resources += productionPerWorker;
            }

            a.resources = Math.max(0.0, a.resources - consumptionPerYear);

            // A stockpile is a real state variable, not merely a visual object.
            StockpileKey key = keyFor(a);
            double stored = stockpiles.getOrDefault(key, 0.0);

            if (a.resources < 0.40 * initialResourcePerAgent && stored > 0.0) {
                double draw = Math.min(
                        stored * stockpileWithdrawFraction,
                        0.40 * initialResourcePerAgent - a.resources
                );
                if (draw > 0.0) {
                    a.resources += draw;
                    stockpiles.put(key, Math.max(0.0, stored - draw));
                    recordEvent(
                            CivEvent.Type.STOCKPILE_WITHDRAWAL,
                            a.group, null, a.x, a.z, draw,
                            "resource withdrawal"
                    );
                }
            }

            if ((a.action == AgentAction.HOARD || a.action == AgentAction.GATHER)
                    && a.resources > initialResourcePerAgent * 0.65) {
                double deposit = Math.min(
                        a.resources * stockpileDepositFraction
                                * (0.6 + 0.8 * a.hoarding),
                        a.resources - initialResourcePerAgent * 0.25
                );
                if (deposit > 0.0) {
                    a.resources -= deposit;
                    stockpiles.put(key, stored + deposit);

                    if (deposit >= 0.5) {
                        recordEvent(
                                CivEvent.Type.STOCKPILE_DEPOSIT,
                                a.group, null, a.x, a.z, deposit,
                                "resource stockpiled"
                        );
                    }
                }
            }
        }
    }

    private void actionPhase() {
        for (SimAgent a : agents) {
            if (!a.alive) continue;
            a.action = chooseAction(a);
        }
    }

    private AgentAction chooseAction(SimAgent a) {
        double scarcity = Math.max(
                0.0,
                1.0 - a.resources / Math.max(initialResourcePerAgent, 1.0)
        );

        double[] w = {
                0.16 + 0.25 * a.hoarding,
                0.08 + 0.28 * scarcity,
                0.10 + 0.22 * (1.0 - a.hoarding),
                0.07 + 0.28 * a.hoarding,
                0.08 + 0.18 * (1.0 - a.deathFear),
                0.06 + 0.18 * a.deathFear,
                0.03 + 0.17 * a.deathFear + 0.05 * scarcity,
                0.02 + 0.09 * a.hoarding + 0.05 * scarcity + 0.07 * a.prejudice
        };

        double total = 0.0;
        for (double v : w) total += Math.max(0.0, v);
        double draw = rng.nextDouble() * total;

        AgentAction[] values = AgentAction.values();
        for (int i = 0; i < values.length; i++) {
            draw -= Math.max(0.0, w[i]);
            if (draw <= 0.0) return values[i];
        }
        return AgentAction.REST;
    }

    private void interactionPhase() {
        List<Integer> alive = aliveIndices();
        if (alive.size() < 2) return;

        int interactions = (int) Math.max(
                1,
                Math.round(interactionRate * alive.size())
        );

        for (int k = 0; k < interactions; k++) {
            int pA = rng.nextInt(alive.size());
            int pB = rng.nextInt(alive.size() - 1);
            if (pB >= pA) pB++;

            SimAgent a = agents.get(alive.get(pA));
            SimAgent b = agents.get(alive.get(pB));

            boolean sameGroup = a.group == b.group;
            double dx = a.x - b.x;
            double dz = a.z - b.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            double proximity = Math.exp(-distance / 0.20);

            double pairPrejudice = sameGroup
                    ? 0.0
                    : 0.5 * (a.prejudice + b.prejudice);

            double cooperationScore =
                    -0.4
                            + 1.2 * baseCooperation
                            + 0.7 * proximity
                            - 1.8 * pairPrejudice
                            - 0.7 * Math.abs(a.hoarding - b.hoarding);

            boolean cooperate = rng.nextDouble() < sigmoid(cooperationScore);

            if (cooperate) {
                double transfer = 0.08 * Math.max(0.0, a.resources - b.resources);
                transfer = Math.min(transfer, a.resources);
                a.resources -= transfer;
                b.resources += transfer;

                if (!sameGroup) {
                    a.prejudice = lower(a.prejudice, cooperationPrejudiceReduction);
                    b.prejudice = lower(b.prejudice, cooperationPrejudiceReduction);
                    a.deathFear = lower(a.deathFear, 0.4 * traitLearningRate);
                    b.deathFear = lower(b.deathFear, 0.4 * traitLearningRate);
                    a.hoarding = lower(a.hoarding, 0.25 * traitLearningRate);
                    b.hoarding = lower(b.hoarding, 0.25 * traitLearningRate);
                    if (proximity > 0.4) {
                        a.prejudice = lower(a.prejudice, contactPrejudiceReduction);
                        b.prejudice = lower(b.prejudice, contactPrejudiceReduction);
                    }
                    if (transfer > 0.0) {
                        recordEvent(
                                CivEvent.Type.COOPERATION,
                                a.group, b.group,
                                0.5 * (a.x + b.x),
                                0.5 * (a.z + b.z),
                                transfer,
                                "cross-group cooperation"
                        );
                    }
                }
            }

            double scarcity = Math.max(
                    0.0,
                    1.0
                            - 0.5 * (a.resources + b.resources)
                            / Math.max(1.0, 2.0 * initialResourcePerAgent)
            );

            double meanDeathFear = 0.5 * (a.deathFear + b.deathFear);
            double pViolence = clamp01(
                    violenceBase
                            + violenceScarcity * scarcity
                            + violencePrejudice * pairPrejudice
                            + 0.05 * (a.hoarding + b.hoarding)
                            - 0.04 * meanDeathFear
            );

            if (rng.nextDouble() < pViolence) {
                totalViolentEvents++;
                if (a.group == Group.A) groupAViolence++;
                if (b.group == Group.A) groupAViolence++;
                if (a.group == Group.B) groupBViolence++;
                if (b.group == Group.B) groupBViolence++;

                double loss = Math.min(
                        Math.min(a.resources, b.resources),
                        0.5 + 0.5 * rng.nextDouble()
                );
                a.resources -= loss;
                b.resources -= loss;

                // Violence is a social experience: it changes future bias.
                a.deathFear = raise(a.deathFear, traitLearningRate * 0.22);
                b.deathFear = raise(b.deathFear, traitLearningRate * 0.22);
                a.hoarding = raise(a.hoarding, traitLearningRate * 0.18);
                b.hoarding = raise(b.hoarding, traitLearningRate * 0.18);

                if (!sameGroup) {
                    a.prejudice = raise(a.prejudice, violencePrejudiceIncrease);
                    b.prejudice = raise(b.prejudice, violencePrejudiceIncrease);
                }

                recordEvent(
                        CivEvent.Type.VIOLENCE,
                        a.group, b.group,
                        0.5 * (a.x + b.x),
                        0.5 * (a.z + b.z),
                        1.0 + loss,
                        sameGroup ? "within-group violence" : "cross-group violence"
                );
            }
        }

        // Small social drift keeps traits fluid rather than permanently frozen.
        for (SimAgent a : agents) {
            if (!a.alive) continue;
            a.prejudice = clamp01(
                    a.prejudice + gaussian(0.0, mutationSd * 0.05)
            );
            a.deathFear = clamp01(
                    a.deathFear + gaussian(0.0, mutationSd * 0.03)
            );
            a.hoarding = clamp01(
                    a.hoarding + gaussian(0.0, mutationSd * 0.03)
            );
        }
    }

    private void progressionPhase() {
        for (Group group : Group.values()) {
            List<SimAgent> members = members(group);
            if (members.isEmpty()) continue;

            double meanResources = members.stream()
                    .mapToDouble(a -> a.resources)
                    .average()
                    .orElse(0.0);

            double meanHoarding = members.stream()
                    .mapToDouble(a -> a.hoarding)
                    .average()
                    .orElse(0.0);

            double hoardingSd = standardDeviation(
                    members.stream().mapToDouble(a -> a.hoarding).toArray()
            );
            double diversity = Math.min(1.0, 4.0 * hoardingSd);

            double expected =
                    progressionBase
                            + progressionResourceScale * Math.max(0.0, meanResources)
                            + progressionDiversityBonus * diversity
                            - 0.05 * meanHoarding;

            int innovations = poissonApprox(Math.max(0.0, expected));

            if (group == Group.A) groupAProgression += innovations;
            else groupBProgression += innovations;

            if (innovations > 0) {
                SimAgent representative = members.get(rng.nextInt(members.size()));
                recordEvent(
                        CivEvent.Type.PROGRESSION,
                        group, null,
                        representative.x, representative.z,
                        innovations,
                        "useful progression"
                );
            }
        }
    }

    private void movementPhase() {
        for (SimAgent a : agents) {
            if (!a.alive) continue;

            double mobility =
                    0.010
                            + 0.018 * (1.0 - a.deathFear)
                            + 0.010 * Math.min(1.0, a.resources / initialResourcePerAgent);

            if (a.action == AgentAction.EXPLORE) mobility += 0.04;
            if (a.action == AgentAction.FLEE) mobility += 0.06;

            if (rng.nextDouble() < clamp01(mobility)) {
                double oldX = a.x;
                double oldZ = a.z;
                a.x = clamp01(a.x + gaussian(0.0, a.action == AgentAction.EXPLORE ? 0.06 : 0.035));
                a.z = clamp01(a.z + gaussian(0.0, a.action == AgentAction.EXPLORE ? 0.06 : 0.035));

                if (a.action == AgentAction.EXPLORE
                        && Math.hypot(a.x - oldX, a.z - oldZ) > 0.08) {
                    recordEvent(
                            CivEvent.Type.MIGRATION,
                            a.group, null, a.x, a.z,
                            Math.hypot(a.x - oldX, a.z - oldZ),
                            "migration/exploration"
                    );
                }
            }
        }
    }

    private void birthPhase() {
        if (agents.size() >= maxPopulation) return;

        List<Integer> reproductive = new ArrayList<>();
        for (int i = 0; i < agents.size(); i++) {
            SimAgent a = agents.get(i);
            if (a.alive && a.age >= reproductiveAge) reproductive.add(i);
        }
        if (reproductive.size() < 2) return;

        for (int parentIndex : reproductive) {
            if (agents.size() >= maxPopulation) break;

            SimAgent parent = agents.get(parentIndex);
            double resourceRatio =
                    parent.resources / Math.max(initialResourcePerAgent, 1.0);
            double pBirth = clamp01(
                    birthRate + resourceBirthBonus * sigmoid(resourceRatio - 1.0)
            );

            if (rng.nextDouble() >= pBirth) continue;

            int partnerIndex = reproductive.get(rng.nextInt(reproductive.size()));
            if (partnerIndex == parentIndex) continue;
            SimAgent partner = agents.get(partnerIndex);

            Group childGroup = rng.nextBoolean() ? parent.group : partner.group;

            SimAgent child = new SimAgent(
                    childGroup,
                    clamp01(0.5 * (parent.x + partner.x) + gaussian(0.0, 0.025)),
                    clamp01(0.5 * (parent.z + partner.z) + gaussian(0.0, 0.025)),
                    0,
                    5.0,
                    inherit(parent.deathFear, partner.deathFear),
                    inherit(parent.hoarding, partner.hoarding),
                    inherit(parent.prejudice, partner.prejudice)
            );
            agents.add(child);

            recordEvent(
                    CivEvent.Type.BIRTH,
                    childGroup, null, child.x, child.z,
                    1.0,
                    "birth"
            );
        }
    }

    private void deathPhase() {
        ArrayList<SimAgent> survivors = new ArrayList<>(agents.size());

        for (SimAgent a : agents) {
            if (!a.alive) continue;

            double scarcity = Math.max(
                    0.0,
                    1.0 - a.resources / Math.max(initialResourcePerAgent, 1.0)
            );
            double ageComponent =
                    ageDeathScale * Math.pow(
                            Math.max(0.0, a.age - reproductiveAge),
                            1.45
                    );

            double pDeath =
                    deathRate
                            + scarcityDeathPenalty * scarcity
                            + ageComponent;

            // Fear of death can encourage avoidance and slightly lower death risk.
            pDeath *= 1.0 - 0.30 * a.deathFear;
            pDeath = clamp01(pDeath);

            if (rng.nextDouble() < pDeath) {
                a.alive = false;
                recordEvent(
                        CivEvent.Type.DEATH,
                        a.group, null, a.x, a.z,
                        1.0,
                        "death"
                );
            } else {
                a.age++;
                survivors.add(a);
            }
        }

        agents.clear();
        agents.addAll(survivors);
    }

    private double inherit(double a, double b) {
        double parentMean = 0.5 * (a + b);
        return clamp01(
                inheritanceStrength * parentMean
                        + (1.0 - inheritanceStrength) * rng.nextDouble()
                        + gaussian(0.0, mutationSd)
        );
    }

    private void recomputeStats() {
        for (GroupStats s : stats.values()) s.clear();

        for (Group group : Group.values()) {
            GroupStats s = stats.get(group);
            List<SimAgent> members = members(group);
            s.population = members.size();
            if (members.isEmpty()) continue;

            double resources = 0.0;
            double fear = 0.0;
            double hoarding = 0.0;
            double prejudice = 0.0;
            double x = 0.0;
            double z = 0.0;

            for (SimAgent a : members) {
                resources += a.resources;
                fear += a.deathFear;
                hoarding += a.hoarding;
                prejudice += a.prejudice;
                x += a.x;
                z += a.z;
            }

            double n = members.size();
            s.meanResources = resources / n;
            s.meanDeathFear = fear / n;
            s.meanHoarding = hoarding / n;
            s.meanPrejudice = prejudice / n;
            s.meanX = x / n;
            s.meanZ = z / n;
            s.density = n;
            s.progression = group == Group.A ? groupAProgression : groupBProgression;
            s.violenceInvolvement = group == Group.A ? groupAViolence : groupBViolence;
            s.stockpileResources = totalStockpile(group);
        }
    }

    public synchronized SimSnapshotData dataForViewer() {
        List<SimAgent> copy = new ArrayList<>(agents.size());
        for (SimAgent a : agents) {
            if (!a.alive) continue;
            copy.add(a);
        }
        return new SimSnapshotData(
                year,
                spatialCells,
                copy,
                stockpiles.entrySet().stream()
                        .map(e -> new StockpileSnapshot(
                                e.getKey().group(),
                                e.getKey().cellX(),
                                e.getKey().cellZ(),
                                e.getValue()
                        ))
                        .filter(s -> s.amount() > 0.01)
                        .toList(),
                recentEventsList(),
                snapshot()
        );
    }

    public synchronized List<SimAgent> sampleAgents(Group group, int count) {
        List<SimAgent> members = members(group);
        if (members.isEmpty() || count <= 0) return List.of();

        int n = Math.min(count, members.size());
        ArrayList<SimAgent> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            result.add(members.get(rng.nextInt(members.size())));
        }
        return result;
    }

    public synchronized SimAgent randomAgent(Group group) {
        List<SimAgent> members = members(group);
        if (members.isEmpty()) return null;
        return members.get(rng.nextInt(members.size()));
    }

    public synchronized StockpileSnapshot nearestStockpile(
            Group group,
            double x,
            double z,
            double minimumAmount
    ) {
        StockpileSnapshot best = null;
        double bestD2 = Double.POSITIVE_INFINITY;

        for (Map.Entry<StockpileKey, Double> e : stockpiles.entrySet()) {
            if (e.getKey().group() != group || e.getValue() < minimumAmount) continue;
            double cx = cellCenter(e.getKey().cellX());
            double cz = cellCenter(e.getKey().cellZ());
            double dx = cx - x;
            double dz = cz - z;
            double d2 = dx * dx + dz * dz;
            if (d2 < bestD2) {
                bestD2 = d2;
                best = new StockpileSnapshot(
                        group, e.getKey().cellX(), e.getKey().cellZ(), e.getValue()
                );
            }
        }
        return best;
    }

    public synchronized int spatialCells() {
        return spatialCells;
    }

    public synchronized double cellCenter(int cell) {
        return (Math.max(0, Math.min(spatialCells - 1, cell)) + 0.5) / spatialCells;
    }

    public synchronized long year() { return year; }
    public synchronized int totalPopulation() { return agents.size(); }
    public synchronized int violentEvents() { return totalViolentEvents; }
    public synchronized int violenceFor(Group group) { return stats.get(group).violenceInvolvement; }
    public synchronized double progressionFor(Group group) { return stats.get(group).progression; }
    public synchronized GroupStats stats(Group group) { return stats.get(group); }
    public synchronized List<StockpileSnapshot> stockpiles() {
        return stockpiles.entrySet().stream()
                .filter(e -> e.getValue() > 0.01)
                .map(e -> new StockpileSnapshot(
                        e.getKey().group(),
                        e.getKey().cellX(),
                        e.getKey().cellZ(),
                        e.getValue()
                ))
                .toList();
    }

    public synchronized CivSnapshot snapshot() {
        GroupStats a = stats.get(Group.A);
        GroupStats b = stats.get(Group.B);
        return new CivSnapshot(
                year,
                a.population + b.population,
                a.population,
                b.population,
                a.meanResources,
                b.meanResources,
                a.meanDeathFear,
                b.meanDeathFear,
                a.meanHoarding,
                b.meanHoarding,
                a.meanPrejudice,
                b.meanPrejudice,
                a.violenceInvolvement,
                b.violenceInvolvement,
                totalViolentEvents,
                groupAProgression,
                groupBProgression,
                groupAProgression + groupBProgression,
                totalStockpile(Group.A),
                totalStockpile(Group.B)
        );
    }

    public synchronized List<CivEvent> drainEvents() {
        ArrayList<CivEvent> out = new ArrayList<>(events);
        events.clear();
        return out;
    }

    private List<CivEvent> recentEventsList() {
        return List.copyOf(events);
    }

    private void recordEvent(
            CivEvent.Type type,
            Group group,
            Group otherGroup,
            double x,
            double z,
            double magnitude,
            String description
    ) {
        CivEvent event = new CivEvent(
                nextEventId++, year, type, group, otherGroup,
                clamp01(x), clamp01(z), magnitude, description
        );
        events.addLast(event);
        while (events.size() > 250) events.removeFirst();
    }

    private List<Integer> aliveIndices() {
        ArrayList<Integer> out = new ArrayList<>();
        for (int i = 0; i < agents.size(); i++) {
            if (agents.get(i).alive) out.add(i);
        }
        return out;
    }

    private List<SimAgent> members(Group group) {
        return agents.stream()
                .filter(a -> a.alive && a.group == group)
                .toList();
    }

    private StockpileKey keyFor(SimAgent a) {
        return new StockpileKey(
                a.group,
                cell(a.x),
                cell(a.z)
        );
    }

    private int cell(double value) {
        return Math.max(
                0,
                Math.min(
                        spatialCells - 1,
                        (int) Math.floor(clamp01(value) * spatialCells)
                )
        );
    }

    private double totalStockpile(Group group) {
        return stockpiles.entrySet().stream()
                .filter(e -> e.getKey().group() == group)
                .mapToDouble(Map.Entry::getValue)
                .sum();
    }

    private int poissonApprox(double lambda) {
        if (lambda <= 0.0) return 0;
        if (lambda < 1.0) return rng.nextDouble() < lambda ? 1 : 0;
        return Math.max(
                0,
                (int) Math.rint(lambda + Math.sqrt(lambda) * gaussian(0.0, 1.0))
        );
    }

    private double gaussian(double mean, double sd) {
        if (sd == 0.0) return mean;
        double u1 = Math.max(1e-12, rng.nextDouble());
        double u2 = rng.nextDouble();
        double z = Math.sqrt(-2.0 * Math.log(u1))
                * Math.cos(2.0 * Math.PI * u2);
        return mean + sd * z;
    }

    private static double sigmoid(double x) {
        double y = Math.max(-30.0, Math.min(30.0, x));
        return 1.0 / (1.0 + Math.exp(-y));
    }

    private static double clamp01(double x) {
        return Math.max(0.0, Math.min(1.0, x));
    }

    private static double raise(double x, double amount) {
        return clamp01(x + amount * (1.0 - x));
    }

    private static double lower(double x, double amount) {
        return clamp01(x - amount * x);
    }

    private static double standardDeviation(double[] values) {
        if (values.length == 0) return 0.0;
        double mean = 0.0;
        for (double v : values) mean += v;
        mean /= values.length;
        double sum = 0.0;
        for (double v : values) {
            double d = v - mean;
            sum += d * d;
        }
        return Math.sqrt(sum / values.length);
    }

    public record SimSnapshotData(
            long year,
            int spatialCells,
            List<SimAgent> agents,
            List<StockpileSnapshot> stockpiles,
            List<CivEvent> events,
            CivSnapshot snapshot
    ) {}
}
