package org.main;

import org.util.BoundingBox;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        File bboxfile = new File("data/bbox/track1.txt");

        // try-with-resources: Scanner will be closed automatically
        try (Scanner myReader = new Scanner(bboxfile)) {
            while (myReader.hasNextLine()) {
                String data = myReader.nextLine();
                BoundingBox b = new BoundingBox(data);
                System.out.println(b);
            }
        } catch (FileNotFoundException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }
}
