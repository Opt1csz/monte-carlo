package io.github.opticsz.civmicroscope;

import io.github.opticsz.civmicroscope.sim.CivSimulation;
import io.github.opticsz.civmicroscope.sim.Group;
import io.github.opticsz.civmicroscope.visual.CivRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.EnumMap;
import java.util.List;

public final class CivMicroscopePlugin extends JavaPlugin
        implements CommandExecutor, TabCompleter {

    private CivSimulation simulation;
    private CivRenderer renderer;
    private LiveStateExporter exporter;

    private final EnumMap<Group, Team> groupTeams =
            new EnumMap<>(Group.class);

    private NamespacedKey proxyKey;
    private NamespacedKey groupKey;
    private boolean running = false;
    private int tickCounter = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        proxyKey = new NamespacedKey(this, "civ_proxy");
        groupKey = new NamespacedKey(this, "civ_group");

        buildSimulation();

        exporter = new LiveStateExporter(getDataFolder().toPath());
        exporter.export(simulation.dataForViewer());

        for (String commandName : List.of("civ", "view")) {
            var command = getCommand(commandName);
            if (command != null) {
                command.setExecutor(this);
                command.setTabCompleter(this);
            }
        }

        // One real Minecraft tick = one scheduler tick. The simulation advances
        // only every configured number of real ticks.
        Bukkit.getScheduler().runTaskTimer(
                this,
                () -> {
                    tickCounter++;

                    if (running
                            && tickCounter % Math.max(
                            1,
                            getConfig().getInt(
                                    "simulation.ticks-per-year",
                                    40
                            )
                    ) == 0) {
                        simulation.step();
                        if (exporter != null
                                && getConfig().getBoolean("export.enabled", true)) {
                            exporter.export(simulation.dataForViewer());
                        }
                    }

                    if (renderer != null) {
                        renderer.tick();
                        if (tickCounter % 20 == 0) {
                            renderer.sendHud();
                        }
                    }
                },
                1L,
                1L
        );

        getLogger().info("CivMicroscope enabled: Minecraft is the microscope; the simulation is the source of truth.");
        getLogger().info("Live viewer state: " + exporter.stateDir().toAbsolutePath());
    }

    @Override
    public void onDisable() {
        running = false;
    }

    private void buildSimulation() {
        FileConfiguration c = getConfig();
        World world = Bukkit.getWorld(c.getString("world", "world"));

        if (world == null) {
            getLogger().severe("World '" + c.getString("world", "world") + "' does not exist.");
            return;
        }

        simulation = new CivSimulation(
                c.getInt("simulation.initial-population", 400),
                c.getInt("simulation.max-population", 4000),
                c.getDouble("simulation.prejudice", 0.30),
                c.getLong("simulation.seed", 42L),
                c.getInt("simulation.spatial-cells", 16),

                c.getDouble("simulation.resource.initial-per-agent", 50.0),
                c.getDouble("simulation.resource.production-per-worker", 2.0),
                c.getDouble("simulation.resource.consumption-per-year", 1.0),
                c.getDouble("simulation.behaviour.base-work-probability", 0.70),
                c.getDouble("simulation.behaviour.interaction-rate-per-agent-year", 0.60),
                c.getDouble("simulation.behaviour.base-cooperation", 0.55),

                c.getDouble("simulation.inheritance.strength", 0.75),
                c.getDouble("simulation.inheritance.mutation-sd", 0.06),
                c.getDouble("simulation.inheritance.trait-learning-rate", 0.03),

                c.getDouble("simulation.demography.birth-rate", 0.045),
                c.getDouble("simulation.demography.resource-birth-bonus", 0.050),
                c.getDouble("simulation.demography.death-rate", 0.020),
                c.getDouble("simulation.demography.scarcity-death-penalty", 0.080),
                c.getDouble("simulation.demography.age-death-scale", 0.00035),
                c.getInt("simulation.demography.reproductive-age", 16),

                c.getDouble("simulation.social.violence-base", 0.03),
                c.getDouble("simulation.social.violence-scarcity", 0.08),
                c.getDouble("simulation.social.violence-prejudice", 0.20),
                c.getDouble("simulation.social.cooperation-prejudice-reduction", 0.035),
                c.getDouble("simulation.social.contact-prejudice-reduction", 0.012),
                c.getDouble("simulation.social.violence-prejudice-increase", 0.055),

                c.getDouble("simulation.outcomes.progression-base", 0.20),
                c.getDouble("simulation.outcomes.progression-resource-scale", 0.004),
                c.getDouble("simulation.outcomes.progression-diversity-bonus", 0.10),

                c.getDouble("simulation.resource.stockpile-deposit-fraction", 0.09),
                c.getDouble("simulation.resource.stockpile-withdraw-fraction", 0.35)
        );

        setupGroupTeams();

        renderer = new CivRenderer(
                this,
                simulation,
                world,
                c.getInt("center-x", 0),
                c.getInt("center-z", 0),
                c.getDouble("view-radius", 120.0),
                Math.max(1, c.getInt("visual.persons-per-villager", 5)),
                Math.max(1, c.getInt("visual.max-villagers-per-group", 60)),
                Math.max(1, c.getInt("visual.update-every-ticks", 2)),
                Math.max(1, c.getInt("visual.retarget-every-ticks", 20)),
                c.getDouble("visual.follow-strength", 0.16),
                c.getDouble("visual.movement-noise", 1.6),
                c.getDouble("visual.vertical-offset", 1.0),
                c.getBoolean("visual.show-names", true),
                c.getBoolean("visual.glowing", true),
                c.getBoolean("visual.show-stockpile-labels", true),
                groupTeams
        );
        renderer.setFollow(c.getBoolean("event-follow.enabled", true));
    }

    private void setupGroupTeams() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        Team a = scoreboard.getTeam("civ_group_a");
        if (a == null) a = scoreboard.registerNewTeam("civ_group_a");
        a.color(net.kyori.adventure.text.format.NamedTextColor.RED);

        Team b = scoreboard.getTeam("civ_group_b");
        if (b == null) b = scoreboard.registerNewTeam("civ_group_b");
        b.color(net.kyori.adventure.text.format.NamedTextColor.AQUA);

        groupTeams.put(Group.A, a);
        groupTeams.put(Group.B, b);
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (!sender.hasPermission("civmicroscope.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }

        boolean bareViewCommand = command.getName().equalsIgnoreCase("view");
        if (bareViewCommand) {
            handleView(sender, args);
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> {
                running = true;
                sender.sendMessage(Component.text("Civilization simulation started.", NamedTextColor.GREEN));
            }
            case "stop", "pause" -> {
                running = false;
                sender.sendMessage(Component.text("Civilization simulation paused.", NamedTextColor.YELLOW));
            }
            case "stats" -> sendStats(sender);
            case "view" -> handleView(sender, args.length > 1 ? java.util.Arrays.copyOfRange(args, 1, args.length) : new String[0]);
            case "reset" -> {
                running = false;
                buildSimulation();
                if (exporter != null && getConfig().getBoolean("export.enabled", true)) {
                    exporter.export(simulation.dataForViewer());
                }
                sender.sendMessage(Component.text(
                        "Simulation reset. Existing visual villagers intentionally remain.",
                        NamedTextColor.AQUA
                ));
            }
            case "setprejudice" -> sender.sendMessage(Component.text(
                    "Prejudice is now an inherited, mutable individual trait. Its initial mean is simulation.prejudice in config.yml.",
                    NamedTextColor.YELLOW
            ));
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleView(CommandSender sender, String[] args) {
        if (renderer == null) {
            sender.sendMessage(Component.text("Renderer not initialized.", NamedTextColor.RED));
            return;
        }

        if (args.length == 0) {
            renderer.toggleFollow();
            renderer.teleportPlayersToView();
            sender.sendMessage(Component.text(
                    "Event following: " + (renderer.isFollowing() ? "ON" : "OFF"),
                    renderer.isFollowing() ? NamedTextColor.GREEN : NamedTextColor.YELLOW
            ));
            return;
        }

        switch (args[0].toLowerCase()) {
            case "on" -> {
                renderer.setFollow(true);
                renderer.jumpToLatest();
                sender.sendMessage(Component.text("Event following ON.", NamedTextColor.GREEN));
            }
            case "off" -> {
                renderer.setFollow(false);
                sender.sendMessage(Component.text("Event following OFF.", NamedTextColor.YELLOW));
            }
            case "now", "latest" -> {
                renderer.jumpToLatest();
                sender.sendMessage(Component.text("Jumped to latest important event.", NamedTextColor.AQUA));
            }
            default -> sender.sendMessage(Component.text("/view [on|off|now]", NamedTextColor.AQUA));
        }
    }

    private void sendStats(CommandSender sender) {
        if (simulation == null) {
            sender.sendMessage(Component.text("Simulation not initialized.", NamedTextColor.RED));
            return;
        }
        var s = simulation.snapshot();
        sender.sendMessage(Component.text(
                String.format(
                        java.util.Locale.ROOT,
                        "Year %d | A pop=%d R=%.2f fear=%.2f hoard=%.2f prejudice=%.2f V=%d P=%.0f stock=%.1f | " +
                                "B pop=%d R=%.2f fear=%.2f hoard=%.2f prejudice=%.2f V=%d P=%.0f stock=%.1f",
                        s.year(),
                        s.groupAPopulation(), s.groupAResources(), s.groupADeathFear(),
                        s.groupAHoarding(), s.groupAPrejudice(), s.groupAViolence(),
                        s.groupAProgression(), s.groupAStockpile(),
                        s.groupBPopulation(), s.groupBResources(), s.groupBDeathFear(),
                        s.groupBHoarding(), s.groupBPrejudice(), s.groupBViolence(),
                        s.groupBProgression(), s.groupBStockpile()
                ),
                NamedTextColor.WHITE
        ));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text(
                "/civ start | stop | reset | stats | view [on|off|now]",
                NamedTextColor.AQUA
        ));
        sender.sendMessage(Component.text(
                "/view toggles following important simulation events.",
                NamedTextColor.GRAY
        ));
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        if (command.getName().equalsIgnoreCase("view")) {
            if (args.length == 1) {
                return List.of("on", "off", "now").stream()
                        .filter(v -> v.startsWith(args[0].toLowerCase()))
                        .toList();
            }
            return List.of();
        }

        if (args.length == 1) {
            return List.of("start", "stop", "reset", "stats", "view").stream()
                    .filter(v -> v.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("view")) {
            return List.of("on", "off", "now").stream()
                    .filter(v -> v.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }

    public NamespacedKey proxyKey() { return proxyKey; }
    public NamespacedKey groupKey() { return groupKey; }
}
