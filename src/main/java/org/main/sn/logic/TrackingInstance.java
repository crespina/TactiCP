package org.main.sn.logic;

import org.main.util.Instance;
import org.main.util.BoundingBox;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.stream.IntStream;

public class TrackingInstance implements Instance {
    // Creates an instance of the problem
    // in : csv files with 6 columns : frame ID, cls ID, top left coordinate of the bounding box, top y coordinate, width, height.
    // e.g. cls_id :
    //      * player team left : 1 2 11 12 13 15 16 19 20 21 22(GK)
    //      * player team right : 3 4 5 6 7 8 9 10 23 24 25(GK)
    //      * referees : 14 17 26
    //      * ball : 18

    public List<BoundingBox> bboxes = new ArrayList<>();
    public int n;

    public int[] players_right_idx, players_left_idx, referees_idx;
    public int ball_idx;
    public int C; //number of classes
    public double[][] dx, dy, acc, dthetas;

    Map<Integer, List<BoundingBox>> frames;

    public TrackingInstance(String instanceFolderPath) {
        String txtFilePath = instanceFolderPath + "/gt/gt.txt";
        File txtFile = new File(txtFilePath);
        try (Scanner myReader = new Scanner(txtFile)) {
            while (myReader.hasNextLine()) {
                String data = myReader.nextLine();
                BoundingBox b = new BoundingBox(data, false);
                bboxes.add(b);
            }
        } catch (FileNotFoundException e) {
            System.out.println("File not found: " + txtFilePath);
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
            System.out.println("Error reading game info file: " + gameInfoPath);
        }

        players_right_idx = playersRight.stream().mapToInt(i -> i).toArray();
        players_left_idx = playersLeft.stream().mapToInt(i -> i).toArray();
        referees_idx = referees.stream().mapToInt(i -> i).toArray();
        C = players_right_idx.length + players_left_idx.length + referees_idx.length + 1;
        int maxId = IntStream.concat(
                IntStream.concat(Arrays.stream(players_right_idx), Arrays.stream(players_left_idx)),
                IntStream.of(ball_idx)
        ).max().orElse(Integer.MIN_VALUE);

        Map<Integer, List<BoundingBox>> frames = new HashMap<>();

        for (BoundingBox b : bboxes) {
            frames.computeIfAbsent(b.frame_id, k -> new ArrayList<>())
                    .add(b);
        }
        this.frames = frames;
        this.n = frames.size();

        Map<Integer, Map<Integer, BoundingBox>> grouped = new HashMap<>();

        for (BoundingBox b : bboxes) {
            if (Arrays.stream(referees_idx).anyMatch(id -> id == b.cls_id)) {
                continue;
            }
            grouped
                    .computeIfAbsent(b.frame_id, k -> new HashMap<>())
                    .putIfAbsent(b.cls_id, b);
        }

        double[][] dx = new double[n + 1][maxId + 1];
        double[][] dy = new double[n + 1][maxId + 1];
        double[][] ax = new double[n + 1][maxId + 1];
        double[][] ay = new double[n + 1][maxId + 1];
        double[][] acc = new double[n + 1][maxId + 1];
        double[][] angles = new double[n + 1][maxId + 1];
        double[][] dthetas = new double[n + 1][maxId + 1];

        // Initialize with NaN or 0 depending on what you want
        for (int i = 0; i <= n; i++) {
            Arrays.fill(dx[i], Double.NaN);
            Arrays.fill(dy[i], Double.NaN);
            Arrays.fill(ax[i], Double.NaN);
            Arrays.fill(ay[i], Double.NaN);
        }

        // Compute instantaneous vectors
        for (var trackEntry : grouped.entrySet()) {
            int frameID = trackEntry.getKey();
            if (frameID == 1) continue;

            for (var clsEntry : trackEntry.getValue().entrySet()) {
                int clsId = clsEntry.getKey();
                BoundingBox curr = clsEntry.getValue();
                BoundingBox prev = grouped.get(frameID - 1).get(clsId);
                if (prev == null) continue;


                dx[frameID][clsId] = curr.x - prev.x;
                dy[frameID][clsId] = curr.y - prev.y;
                ax[frameID][clsId] = dx[frameID][clsId] - dx[frameID - 1][clsId];
                ay[frameID][clsId] = dy[frameID][clsId] - dy[frameID - 1][clsId];
                acc[frameID][clsId] = Math.sqrt(ax[frameID][clsId] * ax[frameID][clsId] + ay[frameID][clsId] * ay[frameID][clsId]);

                angles[frameID][clsId] = Math.atan2(dy[frameID][clsId], dx[frameID][clsId]);
                double dtheta = Math.abs(angles[frameID][clsId] - angles[frameID - 1][clsId]);
                if (dtheta > Math.PI) {
                    dtheta = 2 * Math.PI - dtheta;
                }
                dthetas[frameID][clsId] = dtheta;

            }
        }

        this.dx = dx;
        this.dy = dy;
        this.acc = acc;
        this.dthetas = dthetas;


    }


    @Override
    public String toString() {
        return "TrackingInstance{" +
                "bboxes=" +
                '}' +
                " players right idx=" + Arrays.toString(players_right_idx) +
                " players left idx=" + Arrays.toString(players_left_idx) +
                " ball=" + ball_idx +
                " ref=" + Arrays.toString(referees_idx);
    }


}

