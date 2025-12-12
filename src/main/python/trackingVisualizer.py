import os, csv, argparse, collections, hashlib
import cv2

def color_for_id(tid):
    h = hashlib.md5(str(tid).encode()).digest()
    return (int(h[0]), int(h[1]), int(h[2]))

def find_image(frame_id, img_dir, ext):
    candidates = [f"{frame_id:06d}{ext}"]
    for c in candidates:
        p = os.path.join(img_dir, c)
        if os.path.isfile(p): return p
    return None

def load_detections(det_path):
    frames = collections.defaultdict(list)
    with open(det_path, newline='') as f:
        rdr = csv.reader(f)
        for row in rdr:
            if not row: continue
            # handle files that use commas or spaces
            if len(row) == 1 and ' ' in row[0]:
                row = row[0].split()
            # ensure at least 6 columns
            frame = int(row[0])
            tid = int(row[1])
            x = float(row[2])
            y = float(row[3])
            w = float(row[4])
            h = float(row[5])
            conf = float(row[6]) if len(row) > 6 else 1.0
            frames[frame].append((tid, x, y, w, h, conf))
    return frames

def visualize(det_file, img_dir, out_dir=None, video_path=None, ext='.jpg', show=True, fps=25):
    dets = load_detections(det_file)
    os.makedirs(out_dir, exist_ok=True) if out_dir else None
    video_writer = None
    for frame in sorted(dets):
        img_path = find_image(frame, img_dir, ext)
        if img_path is None:
            print("missing image for frame", frame); continue
        img = cv2.imread(img_path)
        if img is None:
            print("failed to read", img_path); continue
        for (tid,x,y,w,h,conf) in dets[frame]:
            x1,y1 = int(x), int(y)
            x2,y2 = int(x + w), int(y + h)
            col = color_for_id(tid)
            cv2.rectangle(img, (x1,y1), (x2,y2), col, 2)
            label = f"{tid}"
            cv2.putText(img, label, (x1, max(0,y1-6)), cv2.FONT_HERSHEY_SIMPLEX, 0.5, col, 1, cv2.LINE_AA)
        if video_path and video_writer is None:
            h_img, w_img = img.shape[:2]
            fourcc = cv2.VideoWriter_fourcc(*'mp4v')  # .mp4 output
            video_writer = cv2.VideoWriter(video_path, fourcc, fps, (w_img, h_img))

        if show:
            cv2.imshow('annot', img)
            if cv2.waitKey(1) & 0xFF == ord('q'):
                break
        if out_dir:
            out_path = os.path.join(out_dir, os.path.basename(img_path))
            cv2.imwrite(out_path, img)
        if video_writer:
            video_writer.write(img)
    if video_writer:
        video_writer.release()
    cv2.destroyAllWindows()

if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--det", required=True)   # path to det txt/csv
    p.add_argument("--imgdir", required=True) # path to img1
    p.add_argument("--out", default=None)    # save annotated frames here
    p.add_argument("--ext", default=".jpg")
    p.add_argument("--video", default=None, help="output video path (e.g. out.mp4)")
    args = p.parse_args()
    visualize(det_file=args.det,
               img_dir=args.imgdir,
              out_dir=args.out,
              video_path=args.video,
              ext=args.ext,
              show=True)