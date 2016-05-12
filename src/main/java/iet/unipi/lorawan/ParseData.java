package iet.unipi.lorawan;

import org.json.JSONObject;

import java.io.*;
import java.util.*;


public class ParseData implements Runnable {

    private final int N_TEST = 9;

    String[] test = {"250m", "500m L", "500m NL", "1000m", "1500m", "2000m", "2500m rain", "2500m", "3000m"};

    int[] lowerBound = {1, 249, 505, 721, 937, 1156, 1372, 1, 1};
    int[] upperBound = {216, 464, 720, 936, 1152, 1371, 1554, 216, 216};
    int[] metres = {250,500,1000,1005,1500,2000,2400,2500,3000};

    private List<JSONObject>[] lists = new List[N_TEST];


    @Override
    public void run() {

        // Init queues
        for(int i = 0; i < lists.length; i++) {
            lists[i] = new LinkedList<>();
        }


        try (
                BufferedReader in = new BufferedReader(new FileReader("data/received.txt"));
                PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("data/trasp.txt")))

        ) {
            String line;
            int iteration;
            int crc_error = 0;

            /**
             * CARICO TUTTO IN RAM
             */

            read1: for (iteration=0; (line = in.readLine()) != null; iteration++) {

                JSONObject rxpk = new JSONObject(line).getJSONArray("rxpk").getJSONObject(0);


                // Check CRC
                if (rxpk.getInt("stat") != 1) {
                    // TODO: handle wrong crc packets

                    if (rxpk.getInt("size") == 23) {
                        crc_error++;
                    }

                } else {
                    FrameMessage frame = new FrameMessage(new MACMessage(rxpk.getString("data")));
                    System.out.printf("Iter: %d, Frame %d\n", iteration, frame.counter);

                    if (iteration < 16) {
                        continue;
                    } else if (iteration > 1284) {
                        break read1;
                    }


                    for (int i=0; i<N_TEST-2; i++) {

                        if (frame.counter >= lowerBound[i] && frame.counter <= upperBound[i]) {
                            System.out.printf("Iter: %d, Frame %d\n", iteration, frame.counter);
                            lists[i].add(rxpk);
                            break;
                        }
                    }
                }
            }

            read2: for (; (line = in.readLine()) != null; iteration++) {

                JSONObject rxpk = new JSONObject(line).getJSONArray("rxpk").getJSONObject(0);


                // Check CRC
                if (rxpk.getInt("stat") != 1) {
                    // TODO: handle wrong crc packets
                } else {
                    FrameMessage frame = new FrameMessage(new MACMessage(rxpk.getString("data")));
                    System.out.printf("Iter: %d, Frame %d\n", iteration, frame.counter);

                    if (iteration > 1367) {
                        break read2;
                    }



                    if (frame.counter >= lowerBound[7] && frame.counter <= upperBound[7]) {
                        System.out.printf("Iter: %d, Frame %d\n", iteration, frame.counter);
                        lists[7].add(rxpk);
                    }

                }
            }

            read3: for (; (line = in.readLine()) != null; iteration++) {

                JSONObject rxpk = new JSONObject(line).getJSONArray("rxpk").getJSONObject(0);

                // Check CRC
                if (rxpk.getInt("stat") != 1) {
                    // TODO: handle wrong crc packets
                } else {
                    FrameMessage frame = new FrameMessage(new MACMessage(rxpk.getString("data")));
                    System.out.printf("Iter: %d, Frame %d\n", iteration, frame.counter);

                    if (frame.counter >= lowerBound[8] && frame.counter <= upperBound[8]) {
                        System.out.printf("Iter: %d, Frame %d\n", iteration, frame.counter);
                        lists[8].add(rxpk);
                    }

                }
            }


            System.out.println("Tutti i pacchetti sono stati parsati correttamente");
            System.out.println("CRc sbagliato: " + crc_error);


            /**
             * PARSING DEI PACCHETTI
             */

            int test[][][][] = new int[6][9][3][4];

            Map<String,Integer> dr = new HashMap<>();
            dr.put("SF7BW125",5);
            dr.put("SF8BW125",4);
            dr.put("SF9BW125",3);
            dr.put("SF10BW125",2);
            dr.put("SF11BW125",1);
            dr.put("SF12BW125",0);

            Map<String,Integer> cr = new HashMap<>();
            cr.put("4/5",0);
            cr.put("4/6",1);
            cr.put("4/7",2);
            cr.put("4/8",3);

            // Scorro la lista e ottengo le statistiche
            for (int distance=0; distance<lists.length; distance++) {
                // Conto i pacchetti
                for (JSONObject json: lists[distance]) {
                    FrameMessage frame = new FrameMessage(new MACMessage(json.getString("data")));

                    int power = (frame.counter - lowerBound[distance]) / 72;
                    int datr = dr.get(json.getString("datr"));
                    int codr = cr.get(json.getString("codr"));

                    test[datr][distance][power][codr]++;
                }
            }


            Map<Integer,String> pow = new HashMap<>();
            pow.put(0,"14");
            pow.put(1,"8");
            pow.put(2,"2");

            // 6 tabelle, una per ogni data rate
            for (int[][][] table: test) {

                // Per ogni data rate stampo una tabella
                for (int distance=0; distance<table.length; distance++) {

                    // Scarto 1000, 2500 con pioggia e 3000
                    if (distance == 3 || distance == 6 || distance == 8) {
                        continue;
                    }

                    out.printf("%d\t", metres[distance]);

                    for (int power = 0; power<table[distance].length; power++) {

                        for (int codr=0; codr<table[distance][power].length; codr++) {

                            //out.printf("%s_4/%d\t", pow.get(power), codr+5);

                            out.printf("%d\t",table[distance][power][codr]); // save tx power
                        }


                    }

                    out.println();
                }

                out.println();
            }

/*

            out.printf("\t%d\t%d\t%d\t%d\t%d\t%d",dr.get("SF7BW125"),dr.get("SF8BW125"),dr.get("SF9BW125"),dr.get("SF10BW125"),dr.get("SF11BW125"),dr.get("SF12BW125")); // save datr
            out.printf("\t%d\t%d\t%d\t%d\n",cr.get("4/5"),cr.get("4/6"),cr.get("4/7"), cr.get("4/8") ); // save codr

            for (int j=0; j<drArray.length; j++) {
                drByTx[j] += String.format("%d\t%d\t%d\t%d\t%d\t%d\n",drArray[j].get("SF7BW125"),drArray[j].get("SF8BW125"),drArray[j].get("SF9BW125"),drArray[j].get("SF10BW125"),drArray[j].get("SF11BW125"),drArray[j].get("SF12BW125"));
            }
*/

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        ParseData parseData = new ParseData();
        parseData.run();
    }
}
