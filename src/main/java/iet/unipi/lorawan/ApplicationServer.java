package iet.unipi.lorawan;

import org.bouncycastle.util.encoders.Hex;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by alessio on 12/05/16.
 */

public class ApplicationServer {

    public static final String APPS_CONF = "conf/apps.json";
    public static final String MOTES_CONF = "conf/motes.json";

    private static final int MAX_THREADS = 10;

    private final Map<String, Application> apps;
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

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
            app.receiver = new ApplicationServerReceiver(app);
            executor.execute(app.receiver);

        }

    }

    private void loadMotesFromFile(String motesConf) {

        // Scorro tutti i motes

        // Per ogni motes prendo l'app corrsipondente

        // Creo un istanza di LoraMote e la aggiungo alla lista di motes
    }

    private Map<String, Application> loadAppsFromFile(String s) {

        return new ConcurrentHashMap<>();
    }

    void run() {

    }

    public static void main(String[] args) {
        ApplicationServer applicationServer = new ApplicationServer();
        applicationServer.run();
    }
}
