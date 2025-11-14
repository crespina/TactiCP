package org.main;

import org.util.BoundingBox;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

public class SoccerInstance {
    // Creates an instance of the problem
    // in : csv files with 6 columns : frame ID, track ID, top left coordinate of the bounding box, top y coordinate, width, height.

    public List<BoundingBox> bboxes = new ArrayList<>();
    public int n;
    public int[] cls_ids;
    public int[] track_ids;
    public int[] xs;
    public int[] ys;
    public int[] widths;
    public int[] heights;

    public SoccerInstance(String instancePath) {
        File csvFile = new File(instancePath);
        try (Scanner myReader = new Scanner(csvFile)) {
            while (myReader.hasNextLine()) {
                String data = myReader.nextLine();
                BoundingBox b = new BoundingBox(data, false);
                bboxes.add(b);
            }
        } catch (FileNotFoundException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
        n = bboxes.size();
        cls_ids = new int[n];
        track_ids = new int[n];
        xs = new int[n];
        ys = new int[n];
        widths = new int[n];
        heights = new int[n];

        for (int i = 0; i < n; i++) {
            xs[i] = bboxes.get(i).x;
            ys[i] = bboxes.get(i).y;
            widths[i] = bboxes.get(i).width;
            heights[i] = bboxes.get(i).height ;
            cls_ids[i] = bboxes.get(i).cls_id;
            track_ids[i] = bboxes.get(i).track_id;
        }
    }


    @Override
    public String toString() {
        return "SoccerInstance{" +
                "bboxes=" + bboxes +
                '}';
    }
}

