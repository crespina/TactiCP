package org.util;

import java.util.Objects;

/**
 * Defines a bounding box as the yolov11 model outputs it.
 */

public class BoundingBox {

    public int cls_id, box_id;
    public double x1, x2, y1, y2;

    public BoundingBox(int cls_id, int box_id, double x1, double x2, double y1, double y2) {
        this.cls_id = cls_id;
        this.box_id = box_id;
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
    }

    public BoundingBox(String s){
        String[] a_s = s.split(" ");
        this.cls_id = Integer.parseInt(a_s[0]);
        this.box_id = Integer.parseInt(a_s[1]);
        this.x1 = Double.parseDouble(a_s[2]);
        this.x2 = Double.parseDouble(a_s[3]);
        this.y1 = Double.parseDouble(a_s[4]);
        this.y2 = Double.parseDouble(a_s[5]);
    }

    @Override
    public String toString() {
        if (cls_id == 0){
            return "Fastener with ID "+box_id+" at x1 = "+x1+" x2 = "+x2+" y1 = "+y1+" y2 = "+y2;
        } else if (cls_id == 1){
            return "Sleeper with ID "+box_id+" at x1 = "+x1+" x2 = "+x2+" y1 = "+y1+" y2 = "+y2;
        } else {
            return "Neither a fastener or a sleeper";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        org.main.util.BoundingBox that = (org.main.util.BoundingBox) o;
        return cls_id == that.cls_id && box_id == that.box_id && Double.compare(x1, that.x1) == 0 && Double.compare(x2, that.x2) == 0 && Double.compare(y1, that.y1) == 0 && Double.compare(y2, that.y2) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(cls_id, box_id, x1, x2, y1, y2);
    }
}
