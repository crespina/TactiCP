"""
Scalability benchmark data generator.

Builds long instances from SoccerNet Game State clips: the frames of the --inputs clips, in
order, repeated --repeat times and renumbered 1..n. Each requested size n is written to
<output>/<n>/Labels-GameState.json (the first n frames of that sequence).

Repeat a single clip. Track ids are kept as they are, and the engine identifies players and
the ball by track id. Ids restart in every clip, so gluing DIFFERENT clips puts different
players under one id, fixes their team from the first clip, and loses the ball wherever its
track id was a player's in an earlier clip.

Usage (the instances of the paper):
    python src/main/python/scalability_generator.py \
        --inputs data/SoccerNet/gamestate-2024/train/SNGS-061 --repeat 90 \
        --output data/SoccerNet/gamestate-2024/scalability --clean

--sizes takes sizes and start:stop:step ranges (stop included), by default 750 1500
2250:67500:2250: whole copies of a 750-frame clip, up to a 45-minute half. Only the fields the
Java parser reads are written, unless --keep-all-fields is given (a 67,500-frame instance is
then about four times larger). Scalability.java runs every size folder of the output directory:
--clean first removes the size folders already there.
"""

import argparse
import json
import os
import shutil
import sys

DEFAULT_SIZES = ["750", "1500", "2250:67500:2250"]

# what GameStateReconstructionInstance reads, field by field
KEPT = {
    "track_id": None,
    "category_id": None,
    "attributes": ("role", "jersey", "team"),
    "bbox_pitch": ("x_bottom_middle", "y_bottom_middle"),
    "bbox_image": ("x_center", "y_center", "w", "h"),
}


def load_match(match_folder):
    """The frames of a clip, in order: a list of (image, annotations)."""
    json_path = os.path.join(match_folder, "Labels-GameState.json")
    if not os.path.isfile(json_path):
        sys.exit(f"ERROR: {json_path} not found.")
    with open(json_path, "r") as f:
        data = json.load(f)

    ann_by_image = {}
    for ann in data.get("annotations", []):
        if ann.get("image_id") is not None:
            ann_by_image.setdefault(ann["image_id"], []).append(ann)

    images = sorted(data.get("images", []), key=lambda img: int(img["image_id"]))
    frames = [(img, ann_by_image.get(img["image_id"], [])) for img in images]
    return frames, data.get("info", {}), data.get("categories", [])


def parse_sizes(specs):
    sizes = set()
    for spec in specs:
        if ":" in spec:
            start, stop, step = (int(x) for x in spec.split(":"))
            sizes.update(range(start, stop + 1, step))
        else:
            sizes.add(int(spec))
    return sorted(sizes)


def stripped(ann):
    out = {}
    for key, fields in KEPT.items():
        value = ann.get(key)
        if fields is not None and isinstance(value, dict):
            value = {k: value[k] for k in fields if k in value}
        out[key] = value
    return out


def write_instance(path, frames, info, categories, keep_all_fields):
    """Streams the first len(frames) frames to path, renumbered 1..n."""
    with open(path, "w") as f:
        f.write('{"info": ' + json.dumps(dict(info, seq_length=len(frames))))
        f.write(', "categories": ' + json.dumps(categories))

        f.write(', "images": [')
        for number, (image, _) in enumerate(frames, start=1):
            new_image = dict(image) if keep_all_fields else {"file_name": image.get("file_name")}
            new_image["image_id"] = str(number)
            f.write(("," if number > 1 else "") + json.dumps(new_image))

        f.write('], "annotations": [')
        first = True
        for number, (_, anns) in enumerate(frames, start=1):
            for ann in anns:
                new_ann = dict(ann) if keep_all_fields else stripped(ann)
                new_ann["image_id"] = str(number)
                new_ann["id"] = f"{number}_{ann.get('track_id', 0)}"
                f.write(("" if first else ",") + json.dumps(new_ann))
                first = False
        f.write("]}")


def main():
    parser = argparse.ArgumentParser(description="Generate long instances for the scalability benchmark.")
    parser.add_argument("--inputs", "-i", nargs="+", required=True,
                        help="Clip folders containing Labels-GameState.json, concatenated in this order.")
    parser.add_argument("--repeat", "-r", type=int, default=1,
                        help="How many times the concatenated clips are repeated (default: 1).")
    parser.add_argument("--output", "-o", required=True,
                        help="Output directory, e.g. data/SoccerNet/gamestate-2024/scalability")
    parser.add_argument("--sizes", "-s", nargs="+", default=DEFAULT_SIZES,
                        help="Sizes in frames, or start:stop:step ranges (default: %(default)s).")
    parser.add_argument("--keep-all-fields", action="store_true",
                        help="Copy every annotation field, not only those the Java parser reads.")
    parser.add_argument("--clean", action="store_true",
                        help="Remove the size folders already in the output directory before writing.")
    args = parser.parse_args()

    if len(set(args.inputs)) > 1:
        print("WARNING: different clips reuse the same track ids for different players and the ball; "
              "see the module docstring.", file=sys.stderr)

    clips = []
    info, categories = {}, []
    for idx, match_folder in enumerate(args.inputs):
        print(f"Loading clip {idx + 1}: {match_folder}")
        frames, m_info, m_categories = load_match(match_folder)
        clips.append(frames)
        if idx == 0:
            info, categories = m_info, m_categories

    sequence = [frame for _ in range(args.repeat) for frames in clips for frame in frames]
    print(f"{len(sequence)} frames available ({len(args.inputs)} clip(s) x {args.repeat})")

    os.makedirs(args.output, exist_ok=True)
    sizes = parse_sizes(args.sizes)
    existing = [d for d in os.listdir(args.output)
                if d.isdigit() and os.path.isdir(os.path.join(args.output, d))]
    if args.clean:
        for d in existing:
            shutil.rmtree(os.path.join(args.output, d))
        print(f"Removed {len(existing)} size folder(s) from {args.output}")
    else:
        others = sorted((int(d) for d in existing if int(d) not in sizes))
        if others:
            print(f"WARNING: {args.output} also holds sizes {others}, which Scalability.java will run too; "
                  f"use --clean to remove them.", file=sys.stderr)
    for n in sizes:
        if n > len(sequence):
            print(f"  skipping {n}: only {len(sequence)} frames available", file=sys.stderr)
            continue
        size_dir = os.path.join(args.output, str(n))
        os.makedirs(size_dir, exist_ok=True)
        write_instance(os.path.join(size_dir, "Labels-GameState.json"), sequence[:n], info, categories,
                       args.keep_all_fields)
        print(f"  {n:>6} frames -> {size_dir}/")

    print(f"\nDone. Instances written to {args.output}/")


if __name__ == "__main__":
    main()
