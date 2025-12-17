import matplotlib
matplotlib.use("Agg")
import json
import numpy as np
import matplotlib.pyplot as plt
import cv2
from collections import defaultdict
import argparse
from matplotlib.backends.backend_agg import FigureCanvasAgg as FigureCanvas



def load_json(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)

def map_image_ids_to_frames(annotations, total_frames):
    image_ids = sorted({ann["image_id"] for ann in annotations})
    n = len(image_ids)
    if n == total_frames:
        mapping = {img_id: i for i, img_id in enumerate(image_ids)}
    else:
        indices = np.linspace(0, total_frames - 1, num=n, dtype=int)
        mapping = {img_id: int(idx) for img_id, idx in zip(image_ids, indices)}
    return mapping

def build_frame_index(annotations, mapping):
    """
    Build a mapping frame_idx -> list of annotations.
    Drop referees. Keep ball (role == 'ball') even though it has no 'team'.
    """
    frames = defaultdict(list)
    for ann in annotations:
        img_id = ann["image_id"]
        if img_id not in mapping:
            continue
        frame_idx = mapping[img_id]

        # Determine pitch position (use bottom_middle)
        pos = None
        if "bbox_pitch" in ann and ann["bbox_pitch"] is not None:
            bp = ann["bbox_pitch"]
            if "x_bottom_middle" in bp and "y_bottom_middle" in bp:
                pos = (bp["x_bottom_middle"], bp["y_bottom_middle"])
        elif pos is None and "bbox_pitch_raw" in ann and ann["bbox_pitch_raw"] is not None:
            bp = ann["bbox_pitch_raw"]
            if "x_bottom_middle" in bp and "y_bottom_middle" in bp:
                pos = (bp["x_bottom_middle"], bp["y_bottom_middle"])
        if pos is None:
            continue

        attrs = ann.get("attributes", {}) or {}
        role = (attrs.get("role") or "").lower()

        # If there's no 'team' attribute:
        #  - keep only if it's the ball (role == 'ball')
        #  - drop otherwise (referee or unknown)
        if "team" not in attrs or not attrs.get("team"):
            if role == "ball":
                team = "ball"
            else:
                # drop referees and other no-team annotations
                continue
        else:
            team = attrs.get("team", "unknown")

        frames[frame_idx].append({
            "track_id": ann.get("track_id"),
            "team": team,
            "jersey": attrs.get("jersey", ""),
            "role": role,
            "x": float(pos[0]),
            "y": -float(pos[1]),
        })
    return frames

def compute_pitch_limits(all_positions, pad=1.0):
    xs = [p[0] for p in all_positions]
    ys = [p[1] for p in all_positions]
    return (min(xs) - pad, max(xs) + pad, min(ys) - pad, max(ys) + pad)

def draw_and_write_video(frames_positions, total_frames, out_path, fps=25, figsize=(12,7), team_colors=None):
    all_pos = []
    for frame_anns in frames_positions.values():
        for p in frame_anns:
            all_pos.append((p["x"], p["y"]))
    if not all_pos:
        raise RuntimeError("No positions found in annotations to draw.")
    xmin, xmax, ymin, ymax = compute_pitch_limits(all_pos, pad=1.0)

    fig, ax = plt.subplots(figsize=figsize)
    ax.set_xlim(xmin, xmax)
    ax.set_ylim(ymin, ymax)
    ax.set_aspect("equal", adjustable="box")

    width, height = int(figsize[0] * 100), int(figsize[1] * 100)
    fourcc = cv2.VideoWriter_fourcc(*"mp4v")
    writer = cv2.VideoWriter(out_path, fourcc, fps, (width, height))
    if not writer.isOpened():
        raise RuntimeError("Could not open video writer. Check codecs and output path.")

    for f in range(total_frames):
        ax.clear()
        ax.set_xlim(xmin, xmax)
        ax.set_ylim(ymin, ymax)
        ax.set_aspect("equal", adjustable="box")
        ax.set_xlabel("pitch x")
        ax.set_ylabel("pitch y")
        ax.set_title(f"Frame {f+1}/{total_frames}")

        ax.add_patch(plt.Rectangle((xmin, ymin), xmax - xmin, ymax - ymin, fill=False, linewidth=1.0))
        anns = frames_positions.get(f, [])

        for a in anns:

            # pick color
            team = a.get("team", None)
            color = None
            if team_colors is not None:
                color = team_colors.get(team, None)

            # ball
            if a.get("role") == "ball" or team == "ball":
                ax.scatter(a["x"], a["y"], s=40, marker="o",
                           edgecolors="k", linewidths=0.5, color=color)
                ax.text(a["x"], a["y"] + 0.1, "ball",
                        ha="center", va="bottom", fontsize=7)
                continue

            # players
            ax.scatter(a["x"], a["y"], s=150, marker="o",
                       edgecolors="k", linewidths=0.5, color=color)

            label = str(a.get("track_id", ""))
            ax.text(a["x"], a["y"] + 0.2, label,
                    ha="center", va="bottom", fontsize=8, weight="bold")

        # frame → numpy
        fig.canvas.draw()
        buf = fig.canvas.buffer_rgba()
        w, h = fig.canvas.get_width_height()
        img = np.asarray(buf, dtype=np.uint8).reshape(h, w, 4)
        img = img[:, :, :3]

        img = cv2.resize(img, (width, height))
        img_bgr = cv2.cvtColor(img, cv2.COLOR_RGB2BGR)
        writer.write(img_bgr)

    writer.release()
    plt.close(fig)
    print("Video saved to:", out_path)



def main(args):
    data = load_json(args.input)
    annotations = data.get("annotations", [])
    mapping = map_image_ids_to_frames(annotations, args.total_frames)
    frames_raw = build_frame_index(annotations, mapping)
    if args.interpolate:
        frames_all = interpolate_tracks(frames_raw, args.total_frames)
    else:
        frames_all = defaultdict(list)
        for f in range(args.total_frames):
            if f in frames_raw:
                frames_all[f] = frames_raw[f]

    # build color map for teams + ball
    teams = set()
    for lst in frames_all.values():
        for p in lst:
            teams.add(p["team"])
    team_colors = {}
    team_colors["ball"] = "black"
    team_colors["left"] = "white"
    team_colors["right"] = "blue"

    draw_and_write_video(frames_all, args.total_frames, args.output, fps=args.fps, team_colors=team_colors)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Annotations -> pitch video")
    parser.add_argument("--input", "-i", required=True, help="Path to JSON file with annotations")
    parser.add_argument("--output", "-o", default="output.mp4", help="Output video path (mp4)")
    parser.add_argument("--total_frames", "-n", type=int, default=750, help="Total frames in the output video")
    parser.add_argument("--fps", type=int, default=25, help="Video FPS")
    parser.add_argument("--interpolate", action="store_true", help="Interpolate missing player positions between known frames")
    args = parser.parse_args()
    main(args)
