package iet.unipi.lorawan;


import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class Experiment {


    private static final int CONFIGURATION_N = 6*5*4;
    private static final Configuration[] conf = new Configuration[CONFIGURATION_N] ; // dictionary of configuration. Ex: 5 => sf7, cr 4/5, pw 14 dBm


    // Maps to get the indexes (no need for dr map given that they are already in interval [0,5]
    public static final Map<Integer,Integer> lengthIndexes = new HashMap<>();
    public static final Map<String,Integer> crIndexes = new HashMap<>();
    public static final Map<Integer,Integer> powerIndexes = new HashMap<>();

    // Parameters
    private static final Parameters params;

    // Test ID
    private final String devAddress;
    private final int testNumber;

    // Received packets
    private int received = 0;
    private final int[][] packets;

    // Average position
    private float averageLat = 0;
    private float averageLong = 0;

    // Last configuration
    private int lastConf = -1; // last configuration updated
    private int lastLength = -1; // last real length (not index)


    /**
     * Compute static parameters
     */

    static {
        // index = cr * 5 * 6 + pw * 6 + dr
        for (int i=0; i<conf.length; i++) {
            int dr = i % 6;
            int pw = (i % (5*6)) / 6;
            int cr = i / (5*6);

            conf[i] = new Configuration(cr,pw,dr);
        }

        lengthIndexes.put(10,0);
        lengthIndexes.put(50,1);

        crIndexes.put("4/5",0);
        crIndexes.put("4/6",1);
        crIndexes.put("4/7",2);
        crIndexes.put("4/8",3);

        powerIndexes.put(14,0);
        powerIndexes.put(11,1);
        powerIndexes.put(8,2);
        powerIndexes.put(5,3);
        powerIndexes.put(2,4);

        params = loadTestParameters("conf/test.json");
    }


    /**
     * Load parameters from json file
     * @param path path of the json file
     * @return Parameters
     */

    private static Parameters loadTestParameters(String path) {
        // Read json from file
        String file = "{}";
        try {
            file = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.US_ASCII);
        } catch (IOException e) {
            e.printStackTrace();
        }
        JSONObject params = new JSONObject(file);
        boolean plot = params.getBoolean("plot_test");

        JSONArray jsonLengths = params.getJSONArray("lengths");
        int[] lengths = new int[jsonLengths.length()];

        for(int i=0; i<jsonLengths.length(); i++) {
            lengths[i] = lengthIndexes.get(jsonLengths.getInt(i));
        }


        JSONArray jsonCRs = params.getJSONArray("coding_rates");
        int[] crs = new int[jsonCRs.length()];

        for(int i=0; i<jsonCRs.length(); i++) {
            crs[i] = crIndexes.get(jsonCRs.getString(i));
        }


        JSONArray jsonPows = params.getJSONArray("tx_powers");
        int[] pows = new int[jsonPows.length()];

        for(int i=0; i<jsonPows.length(); i++) {
            pows[i] = powerIndexes.get(jsonPows.getInt(i));
        }

        JSONArray jsonDRs = params.getJSONArray("data_rates");
        int[] drs = new int[jsonDRs.length()];

        for(int i=0; i<jsonDRs.length(); i++) {
            drs[i] = jsonDRs.getInt(i);
        }

        int repetitions = params.getInt("repetitions");

        return new Parameters(plot,lengths,crs,pows,drs,repetitions);
    }


    /**
     * Constructor
     * @param devAddress lowercase dev address
     * @param testNumber
     */

    public Experiment(String devAddress, int testNumber) {
        this.devAddress = devAddress.toLowerCase();
        this.testNumber = testNumber;
        this.packets = new int[params.lengths.length][CONFIGURATION_N];
    }


    /**
     * At the end of the experiment print statistics
     * @return
     */

    public String endExperiment() {
        StringBuilder sb = new StringBuilder(300);
        sb.append(String.format("\n\tEnd EXPERIMENT %d of mote %s\n",testNumber, devAddress));
        sb.append(String.format("\tReceived packets: %d\n",received));
        sb.append(String.format("\tAverage position: %f %f\n",averageLat, averageLong));
        return sb.toString();
    }

    /**
     * Print one configuration
     * @param configuration
     * @param length
     * @return
     */

    private String printConfiguration(int configuration, int length) {
        int lengthIndex = lengthIndexes.get(length);
        double per = (1 - (((double)packets[lengthIndex][configuration]) / params.repetitions)) * 100;
        StringBuilder sb = new StringBuilder(300);
        sb.append(String.format("\n\tEnd CONFIG: %d\t  Experiment %d of mote %s\n",configuration, testNumber, devAddress));
        sb.append(String.format("\tLength: %s\t\t  Data rate: %s\t  Coding Rate: %s\t  Trasmission power: %s\n", length, conf[configuration].dr, conf[configuration].cr, conf[configuration].pw));
        sb.append(String.format("\tReceived packets: %d\t  PER: %f %%\n",packets[lengthIndex][configuration],per));
        return sb.toString();
    }


    /**
     * At the end of one configuration print statics
     * @return
     */

    public String printLastConfiguration() {
        return printConfiguration(lastConf,lastLength);
    }


    /**
     * Plot data
     */

    public void plotData(int cr) {
        String path = "data/plot/" + devAddress + "-" + testNumber + ".dat";

        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(path)))) {
            for(int dr: params.dataRates) {
                out.printf("%d",dr);
                for (int len: params.lengths) {
                    for (int pw: params.txPowers) {
                        out.printf("\t%d",packets[len][indexOf(cr,pw,dr)]);
                    }
                }
                out.println();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Add packet to packet array
     * @param configuration
     * @param length
     * @param latitude
     * @param longitude
     */

    public boolean addPacket(int configuration, int length, float latitude, float longitude) {
        Integer lengthIndex = lengthIndexes.get(length);

        if (lengthIndex == null) {
            return false;
        } else {
            packets[lengthIndex][configuration]++;

            // Update average coordinates
            averageLat = (averageLat * received + latitude) / (received+1);
            averageLong = (averageLong * received + longitude) / (received+1);

            // Update received
            received++;
            return true;
        }
    }


    /**
     * Check if it is the first confuguration of this experiment
     * @return
     */

    public boolean isNotFirst() {
        return (lastConf >= 0 && lastLength >= 0);
    }


    /**
     * check if last configuration was equal to parameters passed by argument
     * @param configuration
     * @param length
     * @return
     */

    public boolean lastConfigurationWasNot(int configuration, int length) {
        return (lastConf != configuration || lastLength != length);
    }


    /**
     * Save the actual configuration
     * @param configuration
     * @param length
     */

    public void saveConfiguration(int configuration, int length) {
        lastConf = configuration;
        lastLength = length;
    }


    public static int indexOf(int cr, int pw, int dr) {
        return (cr*5*6) + (pw*6) + dr;
    }
}



