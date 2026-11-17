import matplotlib
matplotlib.use("Agg")
import matplotlib as mpl
import matplotlib.pyplot as plt

mpl.rcParams.update({
    "text.usetex": False,
    "font.family": "serif",
    "font.serif": ["Latin Modern Roman", "DejaVu Serif", "serif"],
    "mathtext.fontset": "cm",   # Computer Modern — the classic LaTeX look
    "font.size": 22,
    "axes.titlesize": 26,
    "axes.labelsize": 24,
    "xtick.labelsize": 20,
    "ytick.labelsize": 20,
})


def draw_zones(ax):
    # main vertical split
    ax.axvline(0, linewidth=1, linestyle="--", color="gray")
    ax.axvline(-31.25, linewidth=1, linestyle="--", color="gray")
    ax.axvline(31.25, linewidth=1, linestyle="--", color="gray")

    # horizontal splits
    ax.hlines(y=-20, xmin=-62.5, xmax=-46,   linestyles="--", linewidth=1, colors="gray")
    ax.hlines(y=-20, xmin=46,    xmax=62.5,  linestyles="--", linewidth=1, colors="gray")
    ax.hlines(y=-20, xmin=-31.25,xmax=31.25, linestyles="--", linewidth=1, colors="gray")
    ax.hlines(y=0,   xmin=-46,   xmax=46,    linestyles="--", linewidth=1, colors="gray")
    ax.hlines(y=20,  xmin=-31.25,xmax=31.25, linestyles="--", linewidth=1, colors="gray")
    ax.hlines(y=20,  xmin=46,    xmax=62.5,  linestyles="--", linewidth=1, colors="gray")
    ax.hlines(y=20,  xmin=-62.5, xmax=-46,   linestyles="--", linewidth=1, colors="gray")

    # extreme side zones
    ax.vlines(x=-46, ymin=-20, ymax=20, linestyles="--", linewidth=1, colors="gray")
    ax.vlines(x=46,  ymin=-20, ymax=20, linestyles="--", linewidth=1, colors="gray")


def annotate_zone(ax, zone_id, x, y):
    ax.text(
        x, y, str(zone_id),
        ha="center", va="center",
        fontsize=22, color="gray", alpha=0.7, weight="bold"
    )


def plot_zones(out_path=None):
    xmin, xmax = -62.5, 62.5
    ymin, ymax = -39, 39

    fig, ax = plt.subplots(figsize=(12, 7))
    ax.set_xlim(xmin, xmax)
    ax.set_ylim(ymin, ymax)
    ax.set_aspect("equal", adjustable="box")

    # Pitch outline
    ax.add_patch(plt.Rectangle(
        (xmin, ymin), xmax - xmin, ymax - ymin,
        fill=False, linewidth=1.5, edgecolor="black"
    ))

    draw_zones(ax)

    # Zone labels
    # central zones
    annotate_zone(ax,  4, -15.6, -10)
    annotate_zone(ax,  3,  15.6, -10)
    annotate_zone(ax,  1, -15.6,  10)
    annotate_zone(ax,  2,  15.6,  10)

    # bottom zones
    annotate_zone(ax, 11, -15.6, -30)
    annotate_zone(ax, 10,  15.6, -30)

    # top zones
    annotate_zone(ax,  6, -15.6,  30)
    annotate_zone(ax,  7,  15.6,  30)

    # side zones
    annotate_zone(ax, 13, -54,  0)
    annotate_zone(ax, 14,  54,  0)

    # corner-ish fallback zones
    annotate_zone(ax, 12, -40, -30)
    annotate_zone(ax,  9,  40, -30)
    annotate_zone(ax,  5, -40,  30)
    annotate_zone(ax,  8,  40,  30)

    plt.tight_layout()

    if out_path:
        fig.savefig(out_path, format="pdf")
        print(f"Saved to {out_path}")
    else:
        plt.show()

    plt.close(fig)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Plot pitch zone decomposition")
    parser.add_argument("--output", "-o", default=None,
                        help="Output image path (e.g. zones.png). If omitted, displays interactively.")
    args = parser.parse_args()
    plot_zones(out_path=args.output)
