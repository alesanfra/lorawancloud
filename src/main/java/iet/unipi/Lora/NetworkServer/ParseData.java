package iet.unipi.Lora.NetworkServer;

import org.json.JSONObject;

import java.io.*;
import java.util.*;


public class ParseData implements Runnable {

    private final int N_TEST = 9;

    String[] test = {"250 m", "500 m LOS", "500 m NLOS", "1000 m", "1500 m", "2000 m", "2500 m rain", "2500 m", "3000 m"};

    int[] lowerBound = {1, 249, 505, 721, 937, 1156, 1372, 1, 1};
    int[] upperBound = {216, 464, 720, 936, 1152, 1371, 1554, 216, 216};

    private List<JSONObject>[] lists = new List[N_TEST];


    @Override
    public void run() {

        // Init queues
        for(int i = 0; i < lists.length; i++) {
            lists[i] = new LinkedList<>();
        }


        try (
                BufferedReader in = new BufferedReader(new FileReader("data/received.txt"));
                PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("data/results.csv")));
                PrintWriter drOut = new PrintWriter(new BufferedWriter(new FileWriter("data/dr.csv")))

        ) {
            String line;
            int iteration;

            read1: for (iteration=0; (line = in.readLine()) != null; iteration++) {

                JSONObject rxpk = new JSONObject(line).getJSONArray("rxpk").getJSONObject(0);


                // Check CRC
                if (rxpk.getInt("stat") != 1) {
                    // TODO: handle wrong crc packets
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

            String[] drByTx = {"","",""};

            // Scorro la lista e ottengo le statistiche
            for (int i=0; i<lists.length; i++) {


                int[] tx = {0,0,0};

                Map<String,Integer> dr = new HashMap<>();
                dr.put("SF7BW125",0);
                dr.put("SF8BW125",0);
                dr.put("SF9BW125",0);
                dr.put("SF10BW125",0);
                dr.put("SF11BW125",0);
                dr.put("SF12BW125",0);

                Map<String,Integer>[] drArray = new Map[3];
                for (int j=0; j<drArray.length; j++) {
                    drArray[j] = new HashMap<>();
                    drArray[j].put("SF7BW125",0);
                    drArray[j].put("SF8BW125",0);
                    drArray[j].put("SF9BW125",0);
                    drArray[j].put("SF10BW125",0);
                    drArray[j].put("SF11BW125",0);
                    drArray[j].put("SF12BW125",0);
                }




                Map<String,Integer> cr = new HashMap<>();
                cr.put("4/5",0);
                cr.put("4/6",0);
                cr.put("4/7",0);
                cr.put("4/8",0);


                for (JSONObject json: lists[i]) {
                    FrameMessage frame = new FrameMessage(new MACMessage(json.getString("data")));

                    int tx_index = (frame.counter - lowerBound[i]) / 72;
                    tx[tx_index]++; // conto la potenza di trasmissione

                    String datr = json.getString("datr");
                    int n = dr.get(datr);
                    dr.put(datr,++n);

                    n = drArray[tx_index].get(datr);
                    drArray[tx_index].put(datr,++n);


                    String codr = json.getString("codr");
                    n = cr.get(codr);
                    cr.put(codr,++n);

                }

                System.out.printf("Test %s, pacchetti %d, 14dBm = %d, 8dBm = %d, 2dBm = %d\n",test[i],lists[i].size(),tx[0],tx[1],tx[2]);
                System.out.printf("Data Rates: %s\n",dr.toString());

                for (Map<String,Integer> map: drArray) {
                    System.out.println(map.toString());
                }

                System.out.printf("Coding Rates: %s\n\n",cr.toString());

                out.printf("%s\t%d\t%d\t%d\t%d",test[i],lists[i].size(),tx[2],tx[1],tx[0]); // save tx power
                out.printf("\t%d\t%d\t%d\t%d\t%d\t%d",dr.get("SF7BW125"),dr.get("SF8BW125"),dr.get("SF9BW125"),dr.get("SF10BW125"),dr.get("SF11BW125"),dr.get("SF12BW125")); // save datr
                out.printf("\t%d\t%d\t%d\t%d\n",cr.get("4/5"),cr.get("4/6"),cr.get("4/7"), cr.get("4/8") ); // save codr

                for (int j=0; j<drArray.length; j++) {
                    drByTx[j] += String.format("%d\t%d\t%d\t%d\t%d\t%d\n",drArray[j].get("SF7BW125"),drArray[j].get("SF8BW125"),drArray[j].get("SF9BW125"),drArray[j].get("SF10BW125"),drArray[j].get("SF11BW125"),drArray[j].get("SF12BW125"));
                }


            }

            for (String s: drByTx) {
                drOut.println(s);
            }


        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        ParseData parseData = new ParseData();
        parseData.run();
    }
}
