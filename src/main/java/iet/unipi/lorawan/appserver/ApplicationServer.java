package iet.unipi.lorawan.appserver;

import iet.unipi.lorawan.Application;
import iet.unipi.lorawan.Mote;
import iet.unipi.lorawan.Util;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by alessio on 12/05/16.
 */

public class ApplicationServer {

    public static final String APPS_CONF = "conf/apps.json";
    public static final String MOTES_CONF = "conf/motes.json";

    private static final int MAX_THREADS = 10;
    private static final int APPSERVER_LISTENING_PORT = 55667;

    private final Map<String, Application> apps;
    private final ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);

    // Sockets
    private final ServerSocket listener;


    public ApplicationServer() {
        // Caricare applicazioni da file
        this.apps = loadAppsFromFile(APPS_CONF);

        // Caricare Mote da file ed assegnarli alla propria applicazione
        loadMotesFromFile(MOTES_CONF);

        /**
         * Per ogni applicazione
         * a. creare un socket TCP verso il NS
         * b. creare un server socket per accettare eventuali CS
         * c.
         */

        for (Map.Entry<String, Application> entry: apps.entrySet()) {
            Application app = entry.getValue();
            //app.receiver = new ApplicationServerReceiver(app);
            //executor.execute(app.receiver);
        }

        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(APPSERVER_LISTENING_PORT);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            listener = serverSocket;
        }

    }

    private void loadMotesFromFile(String motesConf) {
        String file = "{}";

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
            String devAddr = mote.getString("devaddr");

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
            Application app = apps.get(appEUI);

            // Aggiungo il nuovo mote alla lista dell'app corrispondente
            app.motes.put(devAddr,newMote);
        }
    }

    private Map<String, Application> loadAppsFromFile(String appConf) {
        String file = "{}";

        // Read json from file
        try {
            Path path = Paths.get(appConf);
            file = new String(Files.readAllBytes(path), StandardCharsets.US_ASCII);
        } catch (IOException e) {
            e.printStackTrace();
        }

        JSONArray apps = new JSONObject(file).getJSONArray("apps");

        Map<String,Application> map = new ConcurrentHashMap<>();

        // Scorro gli oggetti json e creo le applicazioni
        for (int i=0; i<apps.length(); i++) {
            JSONObject app = apps.getJSONObject(i);
            String appEUI = app.getString("eui");
            String appName = app.getString("name");
            map.put(appEUI, new Application(appEUI,appName));
        }

        return map;
    }

    private void testAS() {
        for (Map.Entry<String, Application> entry: apps.entrySet()) {
            Application app = entry.getValue();

            System.out.println("Applicazione: " + app.name);
            System.out.println("App EUI: " + Util.formatEUI(app.eui));

            for (Map.Entry<String, Mote> entry1: app.motes.entrySet()) {
                System.out.println(entry1.getValue().getDevAddress());
            }
        }
    }

    public void run() {
        testAS();

        while (true) {
            try (
                    Socket customer = listener.accept();
                    BufferedReader in = new BufferedReader(new InputStreamReader(customer.getInputStream(), StandardCharsets.US_ASCII));
            ) {
                String line = in.readLine();

                // Leggo a quale applicazione vuole registrarsi
                JSONObject message = new JSONObject(line);
                String appEUI = message.getString("appeui");

                Application app = apps.get(appEUI);

                // Salvo il socket
                app.sockCS = customer;

                // Faccio partire il tread Receiver
                ApplicationServerSender sender;

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        ApplicationServer applicationServer = new ApplicationServer();
        applicationServer.run();
    }
}
