"""
Scalability benchmark data generator.

Takes Labels-GameState.json files from one or more match folders and creates
merged copies in a scalability output directory, each containing an
incrementally larger number of frames (50, 100, 150, … up to --max).

For sizes beyond a single match (750 frames), frames from additional
matches are appended with remapped image_ids so that the Java parser
(which uses imageId % 10000) sees sequential frame numbers.

Usage:
    python scalability_generator.py \
        --inputs data/SoccerNet/gamestate-2024/train/SNGS-060 \
                 data/SoccerNet/gamestate-2024/train/SNGS-064 \
        --output data/SoccerNet/gamestate-2024/scalability \
        --step 50 --max 1500

The output folder will be:
    data/SoccerNet/gamestate-2024/scalability/
        50/Labels-GameState.json    (first  50 frames)
        100/Labels-GameState.json   (first 100 frames)
        ...
        1500/Labels-GameState.json  (first 1500 frames, merged)
"""

import argparse
import copy
import json
import os
import sys


def load_match(match_folder):
    """Load images and annotations from a match folder, sorted by image_id."""
    json_path = os.path.join(match_folder, "Labels-GameState.json")
    if not os.path.isfile(json_path):
        print(f"ERROR: {json_path} not found.", file=sys.stderr)
        sys.exit(1)

    with open(json_path, "r") as f:
        data = json.load(f)

    images = data.get("images", [])
    annotations = data.get("annotations", [])
    info = data.get("info", {})
    categories = data.get("categories", [])

    images.sort(key=lambda img: img.get("image_id", ""))

    ann_by_image = {}
    for ann in annotations:
        img_id = ann.get("image_id")
        if img_id is not None:
            ann_by_image.setdefault(img_id, []).append(ann)

    return images, ann_by_image, info, categories


def remap_frame(image, anns, new_frame_number):
    """
    Create copies of an image and its annotations with a remapped image_id
    set to the plain sequential frame number (as a string).

    The Java parser handles ids <= 99999 directly and extracts the frame
    number from longer SoccerNet ids via % 1000000.
    """
    new_image_id = str(new_frame_number)

    new_image = copy.deepcopy(image)
    new_image["image_id"] = new_image_id

    new_anns = []
    for ann in anns:
        new_ann = copy.deepcopy(ann)
        new_ann["image_id"] = new_image_id
        new_ann["id"] = f"{new_frame_number}_{ann.get('track_id', 0)}"
        new_anns.append(new_ann)

    return new_image, new_anns


def main():
    parser = argparse.ArgumentParser(
        description="Generate incrementally larger JSON files for scalability benchmarking."
    )
    parser.add_argument(
        "--inputs", "-i",
        nargs="+",
        required=True,
        help="Paths to match folders containing Labels-GameState.json, in order. "
             "Frames from the second match onward are appended after the first.",
    )
    parser.add_argument(
        "--output", "-o",
        required=True,
        help="Output directory for scalability folders "
             "(e.g. data/SoccerNet/gamestate-2024/scalability)",
    )
    parser.add_argument(
        "--step", "-s",
        type=int,
        default=50,
        help="Number of frames to add at each step (default: 50)",
    )
    parser.add_argument(
        "--max", "-m",
        type=int,
        default=1500,
        help="Maximum number of total frames (default: 1500)",
    )
    args = parser.parse_args()

    # ── Load all matches and build a combined frame pool ─────────────
    all_frames = []  # list of (image_dict, [annotation_dicts])
    info = {}
    categories = []

    frame_counter = 0
    for idx, match_folder in enumerate(args.inputs):
        print(f"Loading match {idx + 1}: {match_folder}")
        images, ann_by_image, m_info, m_categories = load_match(match_folder)

        if idx == 0:
            info = m_info
            categories = m_categories

        for image in images:
            frame_counter += 1
            original_image_id = image["image_id"]
            anns = ann_by_image.get(original_image_id, [])

            if idx == 0:
                # First match: keep original image_ids
                all_frames.append((image, anns))
            else:
                # Subsequent matches: remap so % 10000 gives sequential numbers
                new_image, new_anns = remap_frame(image, anns, frame_counter)
                all_frames.append((new_image, new_anns))

    total_available = len(all_frames)
    print(f"Total frames available across {len(args.inputs)} match(es): {total_available}")

    if args.max > total_available:
        print(f"WARNING: requested max={args.max} but only {total_available} frames available. "
              f"Will stop at {total_available}.", file=sys.stderr)
        args.max = total_available

    # ── Output folder ────────────────────────────────────────────────
    os.makedirs(args.output, exist_ok=True)

    # ── Generate truncated copies ────────────────────────────────────
    step = args.step
    sizes = list(range(step, args.max + 1, step))
    if sizes and sizes[-1] != args.max:
        sizes.append(args.max)

    for n in sizes:
        subset_frames = all_frames[:n]
        subset_images = [img for img, _ in subset_frames]
        subset_annotations = []
        for _, anns in subset_frames:
            subset_annotations.extend(anns)

        out_data = {
            "info": info,
            "images": subset_images,
            "annotations": subset_annotations,
            "categories": categories,
        }

        size_dir = os.path.join(args.output, str(n))
        os.makedirs(size_dir, exist_ok=True)
        out_path = os.path.join(size_dir, "Labels-GameState.json")
        with open(out_path, "w") as f:
            json.dump(out_data, f)

        print(f"  {n:>5} frames, {len(subset_annotations):>6} annotations  ->  {size_dir}/")

    print(f"\nDone. {len(sizes)} folders written to {args.output}/")


if __name__ == "__main__":
    main()

