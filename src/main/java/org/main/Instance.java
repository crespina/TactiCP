package org.main;

import org.util.BoundingBox;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Instance {
    // Creates an instance of the problem

    public final int n;
    public List<BoundingBox> bboxes = new ArrayList<>();
    public int[] x_mins;
    public int[] x_maxs;
    public int[] y_mins;
    public int[] y_maxs;
    public int[] cls_ids;
    public int[] box_ids;

    public Instance(String instancePath) {
        File bboxfile = new File(instancePath);
        try (Scanner myReader = new Scanner(bboxfile)) {
            while (myReader.hasNextLine()) {
                String data = myReader.nextLine();
                BoundingBox b = new BoundingBox(data);
                bboxes.add(b);
            }
        } catch (FileNotFoundException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
        n = bboxes.size();
        x_mins = new int[n];
        x_maxs = new int[n];
        y_mins = new int[n];
        y_maxs = new int[n];
        cls_ids = new int[n];
        box_ids = new int[n];

        for (int i = 0; i < n; i++) {
            x_mins[i] = (int) (bboxes.get(i).x1 * 10000);
            x_maxs[i] = (int) (bboxes.get(i).x2 * 10000);
            y_mins[i] = (int) (bboxes.get(i).y1 * 10000);
            y_maxs[i] = (int) (bboxes.get(i).y2 * 10000);
            cls_ids[i] = bboxes.get(i).cls_id;
            box_ids[i] = bboxes.get(i).box_id;
        }
    }
}

