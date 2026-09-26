from __future__ import annotations

import argparse
import time
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd


def read_csv(path: Path) -> pd.DataFrame:
    if not path.exists() or path.stat().st_size == 0:
        return pd.DataFrame()
    try:
        return pd.read_csv(path)
    except (OSError, pd.errors.EmptyDataError, pd.errors.ParserError):
        return pd.DataFrame()


def color_for(group: str) -> str:
    return "#ef4444" if group == "A" else "#22d3ee"


def plot_live(ax, state: Path, max_agents: int, height_field: str) -> str:
    agents = read_csv(state / "agents.csv")
    stock = read_csv(state / "stockpiles.csv")
    stats = read_csv(state / "stats.csv")
    events = read_csv(state / "events.csv")

    ax.clear()
    ax.set_xlabel("geography X")
    ax.set_ylabel("height / trait")
    ax.set_zlabel("geography Z")

    title = "CivMicroscope — waiting for state"
    if not stats.empty:
        latest = stats.iloc[-1]
        year = int(latest["year"])
        title = (
            f"CivMicroscope | year {year} | "
            f"A={int(latest['groupA_population'])} "
            f"B={int(latest['groupB_population'])} | "
            f"violence={int(latest['total_violent_events'])} | "
            f"progression={latest['total_progression']:.0f}"
        )

        # Sample agents for a responsive live view.
        if not agents.empty:
            if len(agents) > max_agents:
                agents = agents.sample(max_agents, random_state=year)

            if height_field == "prejudice":
                h = agents["prejudice"].to_numpy() * 25.0
                hlabel = "prejudice × 25"
            elif height_field == "hoarding":
                h = agents["hoarding"].to_numpy() * 25.0
                hlabel = "hoarding × 25"
            elif height_field == "fear":
                h = agents["death_fear"].to_numpy() * 25.0
                hlabel = "death fear × 25"
            else:
                h = agents["resources"].to_numpy() * 0.35
                hlabel = "resources × 0.35"

            for group, marker in (("A", "o"), ("B", "^")):
                part = agents[agents["group"] == group]
                if part.empty:
                    continue

                if height_field == "prejudice":
                    ph = part["prejudice"].to_numpy() * 25.0
                elif height_field == "hoarding":
                    ph = part["hoarding"].to_numpy() * 25.0
                elif height_field == "fear":
                    ph = part["death_fear"].to_numpy() * 25.0
                else:
                    ph = part["resources"].to_numpy() * 0.35

                ax.scatter(
                    part["x"],
                    ph,
                    part["z"],
                    s=18,
                    marker=marker,
                    c=color_for(group),
                    alpha=0.65,
                    depthshade=True,
                    label=f"Group {group}",
                )

            ax.set_ylabel(hlabel)

        if not stock.empty:
            # Piles are drawn as small elevated squares. Height is stockpile mass.
            for group, c in (("A", "#f59e0b"), ("B", "#a78bfa")):
                part = stock[stock["group"] == group]
                if part.empty:
                    continue
                # Map grid cells to [0,1].
                cells = 16
                if not stats.empty and "spatial_cells" in stats.columns:
                    cells = max(2, int(stats.iloc[-1]["spatial_cells"]))
                sx = (part["cell_x"] + 0.5) / cells
                sz = (part["cell_z"] + 0.5) / cells
                sh = part["amount"] * 0.03 + 0.2
                ax.scatter(
                    sx, sh, sz,
                    s=np.clip(part["amount"] * 6.0, 12, 240),
                    marker="s", c=c, alpha=0.9,
                )

        # Mark the most recent event very visibly.
        if not events.empty:
            e = events.iloc[-1]
            evh = 18.0
            if height_field == "resources":
                evh = 10.0
            ax.scatter(
                [e["x"]], [evh], [e["z"]],
                s=180, marker="*", c="#ffffff", edgecolors="#111827",
                linewidths=1.0, label=f"latest: {e['type']}",
            )

        ax.set_xlim(0, 1)
        ax.set_ylim(bottom=0)
        ax.set_zlim(0, 1)
        try:
            ax.legend(loc="upper left")
        except Exception:
            pass

    ax.set_title(title)
    return title


def main() -> None:
    ap = argparse.ArgumentParser(description="Live semi-3D viewer for CivMicroscope")
    ap.add_argument("--state", required=True, type=Path,
                    help="plugins/CivMicroscope/state directory")
    ap.add_argument("--interval", type=float, default=0.75)
    ap.add_argument("--max-agents", type=int, default=1500)
    ap.add_argument("--height", choices=["resources", "prejudice", "hoarding", "fear"],
                    default="resources")
    args = ap.parse_args()

    plt.ion()
    fig = plt.figure(figsize=(11, 8))
    ax = fig.add_subplot(111, projection="3d")
    fig.canvas.manager.set_window_title("CivMicroscope — live underlying model")

    while plt.fignum_exists(fig.number):
        plot_live(ax, args.state, args.max_agents, args.height)
        fig.canvas.draw_idle()
        fig.canvas.flush_events()
        time.sleep(max(0.05, args.interval))


if __name__ == "__main__":
    main()
