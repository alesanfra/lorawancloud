package iet.unipi.lorawan.appserver;

import iet.unipi.lorawan.Constants;
import iet.unipi.lorawan.Mote;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApplicationServer {

    private static final int MAX_THREADS = 50;


    /**
     * Load application and mote configurations from a json file
     * @param appConf path of the applications conf file
     * @param motesConf path of the motes conf file
     * @return HashMap containg the applications (appEui is the key)
     */

    private static Map<String, Application> loadAppsAndMotes(String appConf, String motesConf) {
        String file = "{}";

        // Read json from file
        try {
            Path path = Paths.get(appConf);
            file = new String(Files.readAllBytes(path), StandardCharsets.US_ASCII);
        } catch (IOException e) {
            e.printStackTrace();
        }

        JSONArray appsJSON = new JSONObject(file).getJSONArray("apps");

        Map<String,Application> applications = new ConcurrentHashMap<>();

        // Scorro gli oggetti json e creo le applicazioni
        for (int i=0; i<appsJSON.length(); i++) {
            JSONObject app = appsJSON.getJSONObject(i);
            String appEUI = app.getString("eui");
            String appName = app.getString("name");
            String address = app.getString("addr");
            int port = app.getInt("port");
            applications.put(appEUI, new Application(appEUI,appName, address, port));
        }


        // LOAD MOTES

        file = "{}";

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

            String appEUI = mote.getString("appeui").toLowerCase();
            String devEUI = mote.getString("deveui").toLowerCase();
            String devAddr = mote.getString("devaddr").toLowerCase();

            // Creo un istanza di Mote e la aggiungo alla lista di motes
            Mote newMote;

            if (mote.getString("join").equals("OTA")) {
                String appKey = mote.getString("appkey");
                newMote = new Mote(
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
                newMote = new Mote(
                        devEUI,
                        appEUI,
                        devAddr,
                        "",
                        netSessKey,
                        appSessKey
                );
            }

            // Per ogni motes prendo l'app corrsipondente
            Application app = applications.get(appEUI);

            // Aggiungo il nuovo mote alla lista dell'app corrispondente
            app.motes.put(devEUI,newMote);
        }

        return applications;
    }


    /*
    private static void testAS(Map<String, Application> apps) {
        for (Map.Entry<String, Application> entry: apps.entrySet()) {
            Application app = entry.getValue();

            System.out.println("Applicazione: " + app.name);
            System.out.println("App EUI: " + Util.formatEUI(app.eui));

            for (Map.Entry<String, Mote> entry1: app.motes.entrySet()) {
                System.out.println(entry1.getValue().getDevAddress());
            }
        }
    }
    */



    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);
        Map<String, Application> apps = loadAppsAndMotes(Constants.APPS_CONF,Constants.MOTES_CONF);

        /**
         * Per ogni applicazione
         * a. creare un socket TCP verso il NS
         * b. I threads sender e receiver
         */

        for (Application app: apps.values()) {
            try {
                //Socket socket = new Socket(Constants.NETSERVER_ADDRESS, Constants.NETSERVER_LISTENING_PORT);
                //app.socket = socket;
                //app.sender = new ApplicationServerSender(app);
                //app.receiver = new ApplicationServerHandler(app);
                executor.execute(new ApplicationServerSender(app));
                executor.execute(new ApplicationServerListener(app));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
