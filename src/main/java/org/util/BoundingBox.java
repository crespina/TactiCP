package org.util;

import java.util.Objects;

public class BoundingBox {
    public int cls_id;
    public int box_id;
    public double x1;
    public double x2;
    public double y1;
    public double y2;

    public BoundingBox(int cls_id, int box_id, double x1, double x2, double y1, double y2) {
        this.cls_id = cls_id;
        this.box_id = box_id;
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
    }

    public BoundingBox(String s) {
        String[] a_s = s.split(" ");
        this.cls_id = Integer.parseInt(a_s[0]);
        this.box_id = Integer.parseInt(a_s[1]);
        this.x1 = Double.parseDouble(a_s[2]);
        this.y1 = Double.parseDouble(a_s[3]);
        this.x2 = Double.parseDouble(a_s[4]);
        this.y2 = Double.parseDouble(a_s[5]);
    }

    public String toString() {
        if (this.cls_id == 0) {
            return "Fastener with ID " + this.box_id + " at x1 = " + this.x1 + " x2 = " + this.x2 + " y1 = " + this.y1 + " y2 = " + this.y2;
        } else {
            return this.cls_id == 1 ? "Sleeper with ID " + this.box_id + " at x1 = " + this.x1 + " x2 = " + this.x2 + " y1 = " + this.y1 + " y2 = " + this.y2 : "Neither a fastener or a sleeper";
        }
    }

    public boolean equals(Object o) {
        if (o != null && this.getClass() == o.getClass()) {
            BoundingBox that = (BoundingBox)o;
            return this.cls_id == that.cls_id && this.box_id == that.box_id && Double.compare(this.x1, that.x1) == 0 && Double.compare(this.x2, that.x2) == 0 && Double.compare(this.y1, that.y1) == 0 && Double.compare(this.y2, that.y2) == 0;
        } else {
            return false;
        }
    }

    public int hashCode() {
        return Objects.hash(new Object[]{this.cls_id, this.box_id, this.x1, this.x2, this.y1, this.y2});
    }
}
