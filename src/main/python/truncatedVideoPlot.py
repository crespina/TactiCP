"""
Execution time on longer instances (trunc3.pdf), at 67,500 frames (barplot67.pdf), and polynomial
fits of the time against the number of frames, read from the CSV written by
org.main.sn.experiments.Scalability (data/scalability/scalability_long.csv).

    python src/main/python/truncatedVideoPlot.py [--time total|solve] [--csv PATH] [--out DIR]

--time total (default): loading the JSON of the instance + solving, as the old figures measured.
--time solve: solving only (model + search).
"""
import argparse
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np

from plotExecTimes import DATA, STYLES, read_times

HALF_TIME = 67500  # frames in a 45-minute half at 25 fps
HATCHES = {"easy": '///', "intermediate": '...', "hard": 'xx'}


def plot_large_queries(series, out):
    """Every instance of the CSV, up to 67,500 frames."""
    fig, ax = plt.subplots()

    for query, style in STYLES.items():
        frames, times = series[query]
        ax.scatter(frames, times, edgecolors='black', s=40, **style)

    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)
    ax.spines['bottom'].set_visible(False)
    ax.spines['left'].set_visible(False)

    ax.set_xlabel("Size of the instance (frames)", fontsize=20)
    ax.set_ylabel("Time (s)", fontsize=20)
    ax.tick_params(axis='both', labelsize=18)
    ax.grid(True, linestyle='--', alpha=0.5)

    ax.legend(loc='upper left', fontsize=18, handletextpad=0.01, borderaxespad=0.01, labelspacing=0.25)
    plt.tight_layout()
    plt.savefig(out / "trunc3.pdf")
    print(f"wrote {out / 'trunc3.pdf'}")
    plt.show()


def barplot67(series, out):
    """The three queries on the 67,500-frame instance."""
    labels = ["Simple", "Intermediate", "Hard"]
    times = [dict(zip(*series[query]))[HALF_TIME] for query in STYLES]
    colors = [style["color"] for style in STYLES.values()]
    x_positions = [0.0, 0.7, 1.4]

    fig, ax = plt.subplots()

    bars = ax.bar(
        x_positions,
        times,
        color=colors,
        width=0.5,
        edgecolor='black',
        linewidth=1.0
    )

    for bar, query in zip(bars, STYLES):
        bar.set_hatch(HATCHES[query])

    ax.set_xticks(x_positions)
    ax.set_xticklabels(labels)

    ax.set_ylabel("Time (s)", fontsize=20)
    ax.tick_params(axis='both', labelsize=20)

    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)

    for x, v in zip(x_positions, times):
        ax.text(x, v, f"{v:.2f}", ha='center', va='bottom', fontsize=18)

    plt.yscale("log")

    plt.tight_layout()
    plt.savefig(out / "barplot67.pdf")
    print(f"wrote {out / 'barplot67.pdf'}")
    plt.show()


def complexity_report(series):
    """R² of least-squares polynomial fits of degree 1 to 3, and the leading coefficients."""
    for query in STYLES:
        frames, times = (np.array(v, dtype=float) for v in series[query])
        ss_tot = np.sum((times - times.mean()) ** 2)
        print(f"{query}:")
        for degree in (1, 2, 3):
            coeffs = np.polyfit(frames, times, degree)
            r2 = 1 - np.sum((times - np.poly1d(coeffs)(frames)) ** 2) / ss_tot
            print(f"  degree {degree}: R² = {r2:.4f}, leading coefficient = {coeffs[0]:.3e}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--time", choices=["total", "solve"], default="total")
    parser.add_argument("--csv", type=Path, default=DATA / "scalability_long.csv")
    parser.add_argument("--out", type=Path, default=DATA)
    args = parser.parse_args()

    series = read_times(args.csv, "frames", args.time)
    plot_large_queries(series, args.out)
    barplot67(series, args.out)
    complexity_report(series)
