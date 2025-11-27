import math
import time

def extract_data(gt, ini):

    bounding_boxes = {}

    with open(gt, 'r') as file:
        for line in file: 
            track, cls, x, y, w, h, _, _, _, _ = line.split(",")
            if int(track) not in bounding_boxes:
                bounding_boxes[int(track)] = {}
            bounding_boxes[int(track)][int(cls)] = (int(x), int(y), int(w), int(h))

    ids = {"left": [], "right": [], "referee": [], "ball": []}
    with open(ini, 'r') as file:
        for line in file :
            if "trackletID" not in line : continue
            line = line.split("=")
            num = int(line[0].split("_")[-1])
            role = line[1].split(";")[0]
            if role == " player team left" or role == " goalkeeper team left":
                ids["left"].append(num)
            elif role == " player team right" or role == " goalkeeper team right":
                ids["right"].append(num)
            elif role == " referee":
                ids["referee"].append(num)
            elif role == " ball":
                ids["ball"].append(num)

    return bounding_boxes, ids

def edge_distance(b1, b2):
    x1,y1,w1,h1 = b1
    x2,y2,w2,h2 = b2
    left = max(x1, x2)
    right = min(x1 + w1, x2 + w2)
    top = max(y1, y2)
    bottom = min(y1 + h1, y2 + h2)
    # if overlap, distance is zero
    if right > left and bottom > top:
        return 0.0
    dx = max(x2 - (x1 + w1), x1 - (x2 + w2), 0.0)
    dy = max(y2 - (y1 + h1), y1 - (y2 + h2), 0.0)
    return math.hypot(dx, dy)

def possession(bounding_boxes, ids):

    possession = {}
    ball_cls = ids["ball"][0]

    for track in bounding_boxes.keys():

        ball_box = bounding_boxes[track].get(ball_cls, None)
        if not ball_box:
            possession[track] = [-1]
            continue

        for cls_id in [*ids["left"], *ids["right"]] :
            player_box = bounding_boxes[track].get(cls_id, None)
            if not player_box:
                continue
            dist = edge_distance(ball_box, player_box)
            if dist < 5 : 
                if track in possession : 
                    possession[track].append(cls_id)
                else : 
                    possession[track] = [cls_id]
        if track not in possession :
            possession[track] = [-1]
    
    return possession

tic = time.time()
gt = "data/SoccerNet/tracking/train/SNMOT-060/gt/gt.txt"
ini = "data/SoccerNet/tracking/train/SNMOT-060/gameinfo.ini"
bboxes, ids = extract_data(gt, ini)
poss = possession(bboxes, ids)
toc = time.time()
print("elapsed time = " + str(toc-tic))
for k in sorted(poss):
    print(k, poss[k])
