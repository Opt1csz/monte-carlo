# CivMicroscope architecture

## Source of truth

`CivSimulation` owns the civilization state.

Minecraft entities are projections of that state.

The external viewer reads a file export of the same state.

```text
                  CivSimulation
                       |
            +----------+----------+
            |                     |
         Renderer              Exporter
            |                     |
        Minecraft             CSV files
                                  |
                                  v
                             viewer.py
```

## Why the social traits are individual

The following are *not* fixed group attributes:

```text
prejudice
hoarding
 deathFear
```

Each individual has a value. This creates distributions inside A and B.

Children inherit these values with partial heritability + mutation.
Interactions move them over time.

Consequently, group means can diverge because of demographic history rather
than because a group was assigned a permanent trait at initialization.

## Why stockpiles are spatial

Stockpiles are keyed by:

```text
(Group, spatial cell X, spatial cell Z)
```

This gives the renderer something physically meaningful to display and creates
a bridge toward the eventual multiscale model, in which local resource density
will influence transition distributions.

## Why event following exists

The simulation contains a history of events with positions.

`/view on` therefore does not randomly move the player. It follows high-impact
points in the actual model:

- violence;
- major stockpiling;
- progression;
- migration;
- births.

The player is effectively sampling the trajectory at interesting causal
locations.

## What is deliberately not implemented yet

- Minecraft villagers affecting the simulation;
- realistic pathfinding / schedules;
- a realistic technology tree;
- network-level social relationships;
- true distributional closure;
- adaptive microscopic refinement;
- deterministic reconstruction of arbitrary historical states.

Those remain future layers rather than being hidden inside the renderer.
