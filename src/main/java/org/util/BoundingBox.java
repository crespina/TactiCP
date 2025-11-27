package org.util;

import java.util.Objects;

public class BoundingBox {
    public boolean xyxy;
    public int cls_id;

    public int box_id;
    public double x1;
    public double x2;
    public double y1;
    public double y2;

    public int x;
    public int y;
    public int track_id;
    public int width;
    public int height;

    public BoundingBox(String s, boolean xyxy) {
        this.xyxy = xyxy;
        if  (xyxy) {
            String[] a_s = s.split(" ");
            this.cls_id = Integer.parseInt(a_s[0]);
            this.box_id = Integer.parseInt(a_s[1]);
            this.x1 = Double.parseDouble(a_s[2]);
            this.y1 = Double.parseDouble(a_s[3]);
            this.x2 = Double.parseDouble(a_s[4]);
            this.y2 = Double.parseDouble(a_s[5]);
        } else {
            String[] a_s = s.split(",");
            this.track_id = Integer.parseInt(a_s[0]);
            this.cls_id = Integer.parseInt(a_s[1]);
            this.x = Integer.parseInt(a_s[2]);
            this.y = Integer.parseInt(a_s[3]);
            this.width = Integer.parseInt(a_s[4]);
            this.height = Integer.parseInt(a_s[5]);
        }

    }

    public String toString() {
        if (xyxy) {
            if (this.cls_id == 0) {
                return "Fastener with ID " + this.box_id + " at x1 = " + this.x1 + " x2 = " + this.x2 + " y1 = " + this.y1 + " y2 = " + this.y2;
            } else {
                return this.cls_id == 1 ? "Sleeper with ID " + this.box_id + " at x1 = " + this.x1 + " x2 = " + this.x2 + " y1 = " + this.y1 + " y2 = " + this.y2 : "Neither a fastener or a sleeper";
            }
        }
        else {
            return "Class " + cls_id + " from track " + track_id + "; coordinates of the top left corner: " + "("+x+","+y+")"+" width = " + width + " height = " + height;
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
