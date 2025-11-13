package org.main;

import org.util.BoundingBox;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

public class Instance {
    // Creates an instance of the problem

    public int n;
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

    public Instance(String[] args){
        ObjectMapper mapper = new ObjectMapper();
        try {
            List<Map<String, Object>> detections = mapper.readValue(args[1], List.class);
            n = detections.size();
            x_mins = new int[n];
            x_maxs = new int[n];
            y_mins = new int[n];
            y_maxs = new int[n];
            cls_ids = new int[n];
            box_ids = new int[n];

            for (int i = 0; i < n; i++) {
                Map<String, Object> det = detections.get(i);
                cls_ids[i] = ((Number) det.get("cls")).intValue();
                box_ids[i] = ((Number) det.get("id")).intValue();
                x_mins[i] = (int) Math.round(((Number) det.get("x1")).doubleValue() * 10000.0);
                y_mins[i] = (int) Math.round(((Number) det.get("y1")).doubleValue() * 10000.0);
                x_maxs[i] = (int) Math.round(((Number) det.get("x2")).doubleValue() * 10000.0);
                y_maxs[i] = (int) Math.round(((Number) det.get("y2")).doubleValue() * 10000.0);
            }
        } catch (Exception e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return "Instance{" +
                "x_mins=" + Arrays.toString(x_mins) +
                ", x_maxs=" + Arrays.toString(x_maxs) +
                ", y_mins=" + Arrays.toString(y_mins) +
                ", y_maxs=" + Arrays.toString(y_maxs) +
                ", cls_ids=" + Arrays.toString(cls_ids) +
                ", box_ids=" + Arrays.toString(box_ids) +
                '}';
    }
}

