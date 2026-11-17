"""
Execution time when games are added one by one (exectime3.pdf), read from the CSV written by
org.main.sn.experiments.Scalability (data/scalability/scalability_games.csv).

    python src/main/python/plotExecTimes.py [--time total|solve] [--csv PATH] [--out DIR]

--time total (default): loading the JSON of the games + solving, as the old figure measured.
--time solve: solving only (model + search).
"""
import argparse
import csv
from pathlib import Path

import matplotlib.pyplot as plt

DATA = Path(__file__).resolve().parents[3] / "data" / "scalability"

STYLES = {
    "easy": dict(marker='o', color='#E69F00', label='Simple Query'),
    "intermediate": dict(marker='s', color='#009E73', label='Intermediate Query'),
    "hard": dict(marker='^', color='#CC79A7', label='Hard Query'),
}


def read_times(csv_path, x_column, time):
    """{query: (xs, seconds)} from a Scalability CSV. Rows of a run that failed are skipped."""
    series = {}
    with open(csv_path, newline="") as f:
        for row in csv.DictReader(f):
            if row["error"]:
                print(f"skipping {row['query']} at {x_column}={row[x_column]}: {row['error']}")
                continue
            ms = int(row["solve_ms"]) + (int(row["load_ms"]) if time == "total" else 0)
            xs, ys = series.setdefault(row["query"], ([], []))
            xs.append(int(row[x_column]))
            ys.append(ms / 1000)
    return series


def plot_three_queries(series, out):
    fig, ax = plt.subplots()

    for query, style in STYLES.items():
        games, times = series[query]
        ax.scatter(games, times, edgecolors='black', s=40, **style)

    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)
    ax.spines['bottom'].set_visible(False)
    ax.spines['left'].set_visible(False)

    ax.set_xlabel("Number of Games", fontsize=20)
    ax.set_ylabel("Time (s)", fontsize=20)
    ax.tick_params(axis='both', labelsize=18)
    ax.grid(True, linestyle='--', alpha=0.5)
    ax.legend(loc='lower right', fontsize=18)

    plt.tight_layout()
    plt.savefig(out / "exectime3.pdf")
    print(f"wrote {out / 'exectime3.pdf'}")
    plt.show()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--time", choices=["total", "solve"], default="total")
    parser.add_argument("--csv", type=Path, default=DATA / "scalability_games.csv")
    parser.add_argument("--out", type=Path, default=DATA)
    args = parser.parse_args()

    plot_three_queries(read_times(args.csv, "games", args.time), args.out)
