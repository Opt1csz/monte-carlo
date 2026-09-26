package io.github.opticsz.civmicroscope.visual;

import io.github.opticsz.civmicroscope.CivMicroscopePlugin;
import io.github.opticsz.civmicroscope.sim.Group;
import io.github.opticsz.civmicroscope.sim.StockpileSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.List;

/** A non-colliding visible representation of a statistical stockpile. */
public final class StockpileVisual {
    private final CivMicroscopePlugin plugin;
    private final Group group;
    private final int cellX;
    private final int cellZ;
    private final List<BlockDisplay> hay = new ArrayList<>();
    private TextDisplay label;

    public StockpileVisual(
            CivMicroscopePlugin plugin,
            Group group,
            int cellX,
            int cellZ
    ) {
        this.plugin = plugin;
        this.group = group;
        this.cellX = cellX;
        this.cellZ = cellZ;
    }

    public void update(
            StockpileSnapshot snapshot,
            Location base,
            boolean showLabels
    ) {
        int targetBlocks = Math.max(
                1,
                Math.min(12, (int) Math.ceil(snapshot.amount() / 15.0))
        );

        while (hay.size() < targetBlocks) {
            int index = hay.size();
            int dx = index % 3;
            int dz = (index / 3) % 2;
            int dy = index / 6;
            Location location = base.clone().add(
                    dx * 0.95 - 0.95,
                    dy * 0.95,
                    dz * 0.95 - 0.475
            );

            BlockDisplay display = base.getWorld().spawn(
                    location,
                    BlockDisplay.class,
                    d -> {
                        d.setBlock(Bukkit.createBlockData(Material.HAY_BLOCK));
                        d.setPersistent(false);
                        d.setViewRange(96.0F);
                        d.setShadowRadius(0.6F);
                        d.setShadowStrength(0.8F);
                    }
            );
            hay.add(display);
        }

        while (hay.size() > targetBlocks) {
            BlockDisplay display = hay.remove(hay.size() - 1);
            display.remove();
        }

        if (showLabels) {
            if (label == null || !label.isValid()) {
                label = base.getWorld().spawn(
                        base.clone().add(0, targetBlocks > 6 ? 2.2 : 1.6, 0),
                        TextDisplay.class,
                        t -> {
                            t.setPersistent(false);
                            t.setBillboard(TextDisplay.Billboard.CENTER);
                            t.setSeeThrough(true);
                            t.setShadowed(true);
                            t.setViewRange(64.0F);
                        }
                );
            }

            String text = String.format(
                    "%s STOCKPILE\n%.1f resources",
                    group == Group.A ? "GROUP A" : "GROUP B",
                    snapshot.amount()
            );
            label.text(net.kyori.adventure.text.Component.text(text));
        }
    }

    public void remove() {
        for (Entity e : hay) e.remove();
        hay.clear();
        if (label != null) label.remove();
        label = null;
    }

    public Group group() { return group; }
    public int cellX() { return cellX; }
    public int cellZ() { return cellZ; }
}
