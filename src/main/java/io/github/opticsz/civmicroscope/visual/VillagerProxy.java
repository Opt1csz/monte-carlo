package io.github.opticsz.civmicroscope.visual;

import io.github.opticsz.civmicroscope.sim.AgentAction;
import io.github.opticsz.civmicroscope.sim.Group;
import io.github.opticsz.civmicroscope.sim.SimAgent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.Random;

/** Persistent visual proxy for a statistical sample. */
public final class VillagerProxy {
    private final Group group;
    private final int serial;
    private final NamespacedKey proxyKey;
    private final NamespacedKey groupKey;
    private final Random random;

    private Villager villager;
    private Location target;
    private AgentAction action = AgentAction.REST;
    private double deathFear;
    private double hoarding;
    private double prejudice;
    private double resources;

    public VillagerProxy(
            Group group,
            int serial,
            NamespacedKey proxyKey,
            NamespacedKey groupKey,
            Random random
    ) {
        this.group = group;
        this.serial = serial;
        this.proxyKey = proxyKey;
        this.groupKey = groupKey;
        this.random = random;
    }

    public void spawn(Location location, boolean glowing, boolean showName) {
        var world = location.getWorld();
        if (world == null) return;

        villager = world.spawn(location, Villager.class, v -> {
            v.setAI(false);
            v.setAware(false);
            v.setInvulnerable(true);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.setCollidable(false);
            v.setCanPickupItems(false);
            v.setSilent(true);
            v.setGlowing(glowing);
            v.setAdult();
            v.setProfession(
                    group == Group.A
                            ? Villager.Profession.FARMER
                            : Villager.Profession.LIBRARIAN
            );

            PersistentDataContainer pdc = v.getPersistentDataContainer();
            pdc.set(proxyKey, PersistentDataType.INTEGER, serial);
            pdc.set(groupKey, PersistentDataType.STRING, group.name());
        });

        updateName(showName);
    }

    public void adopt(Villager existing) {
        this.villager = existing;
        existing.setAI(false);
        existing.setAware(false);
        existing.setInvulnerable(true);
        existing.setPersistent(true);
        existing.setRemoveWhenFarAway(false);
        existing.setCollidable(false);
        existing.setCanPickupItems(false);
        existing.setSilent(true);
        updateName(true);
    }

    public boolean isValid() {
        return villager != null && villager.isValid();
    }

    public Villager entity() { return villager; }
    public Group group() { return group; }
    public AgentAction action() { return action; }
    public Location target() { return target; }

    public void setSample(
            SimAgent sample,
            Location newTarget,
            double jitter,
            boolean showName
    ) {
        if (sample == null || !isValid()) return;

        this.deathFear = sample.deathFear;
        this.hoarding = sample.hoarding;
        this.prejudice = sample.prejudice;
        this.resources = sample.resources;
        this.action = sample.action;

        this.target = newTarget.clone().add(
                gaussian(0.0, jitter),
                0.0,
                gaussian(0.0, jitter)
        );
        updateName(showName);
    }

    public void tick(
            double followStrength,
            double movementNoise,
            boolean showName
    ) {
        if (!isValid() || target == null) return;

        Location current = villager.getLocation();
        double dx = target.getX() - current.getX();
        double dz = target.getZ() - current.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        if (distance > 0.12) {
            double nx = dx / distance;
            double nz = dz / distance;
            double step = Math.min(0.45, followStrength * distance);
            current.add(
                    nx * step + gaussian(0.0, movementNoise * 0.045),
                    0.0,
                    nz * step + gaussian(0.0, movementNoise * 0.045)
            );
        }

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        current.setYaw(yaw);
        villager.teleport(current);

        if (random.nextDouble() < 0.06) {
            emitActionParticle(current);
        }

        if (showName) updateName(true);
    }

    private void updateName(boolean visible) {
        if (!isValid()) return;

        NamedTextColor color = group == Group.A
                ? NamedTextColor.RED
                : NamedTextColor.AQUA;

        String text = String.format(
                "[%s] #%03d %s | R %.0f | F %.2f | H %.2f | X %.2f",
                group == Group.A ? "A" : "B",
                serial,
                action.name(),
                resources,
                deathFear,
                hoarding,
                prejudice
        );
        villager.customName(Component.text(text, color));
        villager.setCustomNameVisible(visible);
    }

    private void emitActionParticle(Location at) {
        Particle.DustOptions dust = new Particle.DustOptions(
                group == Group.A ? Color.RED : Color.AQUA,
                1.0F
        );

        switch (action) {
            case WORK -> at.getWorld().spawnParticle(
                    Particle.DUST, at.clone().add(0, 1.2, 0),
                    2, 0.2, 0.15, 0.2, 0, dust
            );
            case TRADE -> at.getWorld().spawnParticle(
                    Particle.HAPPY_VILLAGER, at.clone().add(0, 1.2, 0),
                    2, 0.25, 0.15, 0.25, 0
            );
            case HOARD -> at.getWorld().spawnParticle(
                    Particle.COMPOSTER, at.clone().add(0, 1.0, 0),
                    2, 0.20, 0.15, 0.20, 0
            );
            case GATHER -> at.getWorld().spawnParticle(
                    Particle.ITEM, at.clone().add(0, 1.0, 0),
                    2, 0.25, 0.20, 0.25, 0
            );
            case EXPLORE -> at.getWorld().spawnParticle(
                    Particle.END_ROD, at.clone().add(0, 1.2, 0),
                    1, 0.15, 0.15, 0.15, 0
            );
            case FLEE -> at.getWorld().spawnParticle(
                    Particle.CLOUD, at.clone().add(0, 1.0, 0),
                    2, 0.20, 0.20, 0.20, 0
            );
            case FIGHT -> {
                at.getWorld().spawnParticle(
                        Particle.CRIT, at.clone().add(0, 1.0, 0),
                        3, 0.20, 0.20, 0.20, 0
                );
                villager.swingMainHand();
            }
            case REST -> { }
        }
    }

    private double gaussian(double mean, double sd) {
        double u1 = Math.max(1e-12, random.nextDouble());
        double u2 = random.nextDouble();
        double z = Math.sqrt(-2.0 * Math.log(u1))
                * Math.cos(2.0 * Math.PI * u2);
        return mean + sd * z;
    }
}
