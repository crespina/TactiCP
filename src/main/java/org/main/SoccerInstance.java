package org.main;

import org.util.BoundingBox;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.*;

public class SoccerInstance implements Instance {
    // Creates an instance of the problem
    // in : csv files with 6 columns : frame ID, cls ID, top left coordinate of the bounding box, top y coordinate, width, height.
    // e.g. cls_id :
    //      * player team left : 1 2 11 12 13 15 16 19 20 21 22(GK)
    //      * player team right : 3 4 5 6 7 8 9 10 23 24 25(GK)
    //      * referees : 14 17 26
    //      * ball : 18

    public List<BoundingBox> bboxes = new ArrayList<>();
    public int n;
    public int[] cls_ids;
    public int[] track_ids;
    public int[] xs;
    public int[] ys;
    public int[] widths;
    public int[] heights;

    public int[] players_right_idx;
    public int[] players_left_idx;
    public int[] referees_idx;
    public int ball_idx;
    public int C; //number of classes
    public List<Integer> noBall; //frames where there is no bbox for the ball
    public Map<Integer, BoundingBox> ball_pos;

    public SoccerInstance(String instanceFolderPath) {
        String txtFilePath = instanceFolderPath + "/gt/gt.txt";
        File txtFile = new File(txtFilePath);
        try (Scanner myReader = new Scanner(txtFile)) {
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
            heights[i] = bboxes.get(i).height;
            cls_ids[i] = bboxes.get(i).cls_id;
            track_ids[i] = bboxes.get(i).track_id;
        }

        String gameInfoPath = instanceFolderPath + "/gameinfo.ini";

        List<Integer> playersRight = new ArrayList<>();
        List<Integer> playersLeft = new ArrayList<>();
        List<Integer> referees = new ArrayList<>();

        File gameInfoFile = new File(gameInfoPath);
        try (Scanner myReader = new Scanner(gameInfoFile)) {

            while (myReader.hasNextLine()) {
                String line = myReader.nextLine();

                if (!line.contains("=") || !line.contains("trackletID")) continue;

                // trackletID_12= player team left;25
                String[] parts = line.split("=");
                String left = parts[0].trim();           // trackletID_12
                String right = parts[1].trim();          // player team left;25

                int id = Integer.parseInt(left.replace("trackletID_", ""));

                String type = right.split(";")[0].trim();  // player team left

                if (type.startsWith("player team right") || type.startsWith("goalkeeper team right")) {
                    playersRight.add(id);
                } else if (type.startsWith("player team left") || type.startsWith("goalkeeper team left")) {
                    playersLeft.add(id);
                } else if (type.startsWith("referee")) {
                    referees.add(id);
                } else if (type.startsWith("ball")) {
                    ball_idx = id;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        players_right_idx = playersRight.stream().mapToInt(i -> i).toArray();
        players_left_idx = playersLeft.stream().mapToInt(i -> i).toArray();
        referees_idx = referees.stream().mapToInt(i -> i).toArray();
        C = players_right_idx.length + players_left_idx.length + referees_idx.length + 1;

        Set<Integer> allFrames = new TreeSet<>();
        for (int i = 0; i < n; i++) allFrames.add(track_ids[i]);

        // frames that contain at least one ball bbox (cls_id == ball_idx)
        Set<Integer> framesWithBall = new HashSet<>();
        for (int i = 0; i < n; i++) {
            if (cls_ids[i] == ball_idx) framesWithBall.add(track_ids[i]);
        }

        // frames without ball = allFrames \ framesWithBall
        List<Integer> noBall = new ArrayList<>();
        for (Integer f : allFrames) if (!framesWithBall.contains(f)) noBall.add(f);

        this.noBall = noBall;
        this.ball_pos = ballBoxes();

    }


    @Override
    public String toString() {
        return "SoccerInstance{" +
                "bboxes=" +
                '}' +
                " players right idx=" + Arrays.toString(players_right_idx) +
                " players left idx=" + Arrays.toString(players_left_idx) +
                " ball=" + ball_idx +
                " ref=" + Arrays.toString(referees_idx);
    }

    /**
     * Returns a sorted list of frame IDs where no bbox has cls_id == ball_idx.
     */
    public List<Integer> framesWithoutBall() {
        // all frame IDs present in the instance
        Set<Integer> allFrames = new TreeSet<>();
        for (int i = 0; i < n; i++) allFrames.add(track_ids[i]);

        // frames that contain at least one ball bbox (cls_id == ball_idx)
        Set<Integer> framesWithBall = new HashSet<>();
        for (int i = 0; i < n; i++) {
            if (cls_ids[i] == ball_idx) framesWithBall.add(track_ids[i]);
        }

        // frames without ball = allFrames \ framesWithBall
        List<Integer> noBall = new ArrayList<>();
        for (Integer f : allFrames) if (!framesWithBall.contains(f)) noBall.add(f);

        return noBall;
    }

    public Map<Integer, BoundingBox> ballBoxes() {
        Map<Integer, BoundingBox> out = new HashMap<>();

        for (int i = 0; i < n; i++) {
            if (cls_ids[i] == ball_idx) {
                int frame = track_ids[i];
                out.put(frame, bboxes.get(i));
            }
        }

        return out;
    }



}

