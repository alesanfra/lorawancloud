package iet.unipi.lorawan;

import org.bouncycastle.util.encoders.Hex;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;


public class ParseData implements Runnable {


    private class Range {
        public final int min, max;
        public Range(int min, int max) {
            this.min = min;
            this.max = max;
        }
    }

    // Data Structures
    private final Map<String,LoraMote> motes;
    private final BlockingQueue<Message> messages;
    private List<JSONObject> packets = new LinkedList<>();
    private final Thread packetAnalyzer;

    public static final String MOTES_CONF = "conf/motes.json";

    private final int[] distances = {500,1000,1500,2000,2500};


    private final int[][][][] stats = new int[6][6][2][5]; // dr, dist, len, pw


    /**
     * Cobstructor
     */

    public ParseData() {
        this.motes = loadMotesFromFile(MOTES_CONF);
        this.messages = new LinkedBlockingQueue<>();

        // Start packet analyzer
        packetAnalyzer = new PacketAnalyzer(messages,motes,stats);
        packetAnalyzer.start();
    }


    /**
     * Load mote configuration form file
     * @param motesConf
     * @return
     */

    public Map<String,LoraMote> loadMotesFromFile(String motesConf) {
        String file = "{}";
        Map<String, LoraMote> map = new ConcurrentHashMap<>();

        // Read json from file
        try {
            Path path = Paths.get(motesConf);
            file = new String(Files.readAllBytes(path), StandardCharsets.US_ASCII);
        } catch (IOException e) {
            e.printStackTrace();
        }

        JSONArray motes = new JSONObject(file).getJSONArray("motes");

        // Scorro tutti i motes
        for (int i=0; i<motes.length(); i++) {
            JSONObject mote = motes.getJSONObject(i);

            String appEUI = mote.getString("appeui");
            String devEUI = mote.getString("deveui");
            String devAddr = mote.getString("devaddr").toLowerCase();

            // Creo un istanza di LoraMote e la aggiungo alla lista di motes
            LoraMote newMote;

            if (mote.getString("join").equals("OTA")) {
                String appKey = mote.getString("appkey");
                newMote = new LoraMote(
                        devEUI,
                        appEUI,
                        devAddr,
                        appKey,
                        "",
                        ""
                );
            } else {
                String netSessKey = mote.getString("netsesskey");
                String appSessKey = mote.getString("appsesskey");
                newMote = new LoraMote(
                        devEUI,
                        appEUI,
                        devAddr,
                        "",
                        netSessKey,
                        appSessKey
                );
            }

            // Aggiungo il nuovo mote alla lista
            map.put(devAddr,newMote);
        }

        return map;
    }


    /**
     * Body of thread
     */

    @Override
    public void run() {

        try (
                BufferedReader in = new BufferedReader(new FileReader("data/received.txt"));
        ) {
            String line;
            int iteration;
            int crc_error = 0;

            /**
             * CARICO TUTTO IN RAM
             */

            for (iteration = 0; (line = in.readLine()) != null; iteration++) {

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

                    // Add message to queue
                    LoraMote mote = motes.get(frame.getDevAddress());
                    byte[] decrypted = frame.getDecryptedPayload(mote.appSessionKey);
                    messages.add(new Message(frame.getDevAddress(), decrypted));

                    packets.add(rxpk);
                }
            }


            System.out.println("Tutti i pacchetti sono stati parsati correttamente");
            System.out.println("CRC sbagliato: " + crc_error);


            // Aggiungo un pacchetto fake per finire l'analisi
            messages.add(new Message("a1b20003", Hex.decode("00000000000000007F00")));


            Range[] configs = {new Range(4, 2),new Range(3, 0),new Range(2, 0),new Range(2, 0),new Range(2, 0)};


            // Leggo gli esperimenti
            packetAnalyzer.join();

            for (int dr = 0; dr <= 5; dr++) {
                String path = String.format("data/parsed/SF%d.dat", 12 - dr);
                try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(path)))) {

                    for (int dist=0; dist<5; dist++) {
                        out.printf("%d",distances[dist]);

                        for (int len=0; len<2; len++) {
                            for (int pw=0; pw<5; pw++) {
                                if (pw == 1) {
                                    continue;
                                }

                                Range range = configs[dist];

                                if (pw != range.min && pw != range.max) {
                                    out.printf("\t?%d", stats[dr][dist][len][pw]);
                                } else {
                                    out.printf("\t%d", stats[dr][dist][len][pw]);
                                }
                            }
                        }
                        out.println();
                    }



                } catch(IOException e){
                e.printStackTrace();
            }
        }






        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    /**
     * Entry point
     * @param args
     */

    public static void main(String[] args) {
        ParseData parseData = new ParseData();
        parseData.run();
    }
}
