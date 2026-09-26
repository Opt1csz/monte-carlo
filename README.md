# CivMicroscope

Live Minecraft microscope + optional semi-3D state viewer for the stochastic
civilization model.

## What changed in this version

### Fluid, inherited social traits

Each simulated person now has three mutable behavioural traits:

- death-fear
- hoarding
- out-group prejudice

All three are inherited from parents with mutation. They can also change from
social experience. Cross-group cooperation reduces prejudice; cross-group
violence increases it; repeated contact can reduce it; violence also pushes
fear and hoarding upward. The simulation therefore does not have a single
fixed `prejudice` variable after initialization.

`simulation.prejudice` is only the initial mean of the individual prejudice
distribution.

### Actual stockpiles

Resources are now stockpiled into spatial Group-A/Group-B stores.
Stockpiles are real state variables that can be withdrawn under scarcity.

Minecraft renders those stores as persistent, non-colliding hay-bale display
blocks. Villagers whose sampled action is `HOARD` or `GATHER` target the relevant
stockpile. When they reach it, contribution particles appear.

### Event-driven microscope

The simulation records births, deaths, migrations, progression, cooperation,
violence and stockpiling as events.

`/view` toggles automatic event following.

```text
/view       toggle event following + move to view
/view on    follow important events
/view off   stop following
/view now   jump to latest important event
```

Violence, stockpiling, progression, births and migration have different visual
particle signatures. This is intended to make the world less visually static.

### Persistent visual population

Visible villagers are statistical proxies. Their count starts from simulated
population density and the renderer only ever adds new proxies. It does not
remove proxies when the simulated population falls.

Each proxy repeatedly samples a living simulated member of its own group and
follows that group's spatial/behavioural distribution with random noise.

If a group becomes extinct, its old visual proxies remain as historical
microscope traces rather than being despawned.

### Group identification

Group A:

- red glowing outline
- `[A]` name tag
- farmer profession

Group B:

- aqua glowing outline
- `[B]` name tag
- librarian profession

A/B also have separate scoreboard teams.

### External semi-3D viewer

The plugin writes live state to:

```text
plugins/CivMicroscope/state/
```

including:

```text
stats.csv
agents.csv
stockpiles.csv
events.csv
READY
```

`viewer/viewer.py` displays the underlying simulation as a live 3-D scatter
plot. Horizontal dimensions are simulation geography; vertical height can be
resources, prejudice, hoarding or death-fear.

This is intentionally a separate program because Minecraft is the visual
microscope and the Python viewer is the statistical microscope.

## Build

Target: Paper 1.21.11, Java 21.

From the project root:

```powershell
powershell.exe -ExecutionPolicy Bypass -File .\build.ps1
```

or, once the local Gradle distribution exists:

```powershell
.\.gradle-bootstrap\gradle-8.10.2\bin\gradle.bat clean build
```

The jar is:

```text
build/libs/civ-microscope-1.0.0.jar
```

Put it into the Paper server's `plugins` directory.

## Minecraft configuration

Edit:

```text
plugins/CivMicroscope/config.yml
```

the first time the server starts.

Important settings:

```yaml
simulation:
  initial-population: 400
  max-population: 4000
  prejudice: 0.30
  ticks-per-year: 40

visual:
  persons-per-villager: 5
  max-villagers-per-group: 60
  movement-noise: 1.6

event-follow:
  enabled: true

export:
  enabled: true
```

`persons-per-villager` controls visual density. It does not change the
simulation population.

## First run

In game:

```text
/civ view
/civ start
```

You can inspect state with:

```text
/civ stats
```

Pause:

```text
/civ stop
```

Reset the numerical simulation:

```text
/civ reset
```

Existing Minecraft proxy villagers are deliberately not removed by reset.

## 3-D viewer

First install Python dependencies:

```powershell
python -m pip install -r .\viewer\requirements.txt
```

Then start the live viewer, replacing the path with your actual server path:

```powershell
python .\viewer\viewer.py --state "C:\Minecraft\CivServer\plugins\CivMicroscope\state"
```

or:

```powershell
powershell.exe -ExecutionPolicy Bypass -File .\viewer\start_viewer.ps1 `
  -StatePath "C:\Minecraft\CivServer\plugins\CivMicroscope\state"
```

Try alternative vertical dimensions:

```powershell
python .\viewer\viewer.py --state "...\state" --height prejudice
python .\viewer\viewer.py --state "...\state" --height hoarding
python .\viewer\viewer.py --state "...\state" --height fear
```

You can run this while Minecraft is running.

## Architecture

```text
             stochastic civilization
                      |
          +-----------+-----------+
          |                       |
       events                  state P
          |                       |
          v                       v
      Minecraft                3-D viewer
      microscope             statistical view
```

The renderer never becomes the source of truth. The simulation remains the
model, which makes it possible to replace the current explicit-agent core with
the distributional/multiscale engine later.


## Visual additions in this revision

### Stockpile contribution loop

A simulated agent with `HOARD` or `GATHER` action targets its own group's
nearest real stockpile. In Minecraft it walks toward that pile and emits
contribution particles on arrival. The hay pile size is determined by the
underlying stored resource amount.

### Event microscope

`/view` follows important model events instead of random locations. Violence,
major stockpiling, births, migration and progression are spatially localized
and have different particle signatures.

### Density signals

The eight densest group/cell combinations periodically emit a faint red or
cyan dust signal. This gives a visible sense of population concentration while
remaining separate from the agents themselves.

### Restart persistence

Visual villagers carry persistent data identifying their group and proxy ID.
On server restart the renderer adopts existing proxy villagers instead of
spawning duplicates. Stockpile display entities regenerate from the numerical
state.
