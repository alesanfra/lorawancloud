package iet.unipi.lorawan;


import org.bouncycastle.util.encoders.Hex;

public class Constants {
    public static final int EUI_HEX_LENGTH = 16;
    public static final int EUI_BYTE_LENGTH = 8;
    public static final int DEV_NONCE_LENGTH = 2;
    public static final int NETSERVER_LISTENING_PORT = 55667;
    public static final String NETSERVER_ADDRESS = "localhost";

    public static final int GATEWAYS_LISTENING_PORT = 1700;
    public static final String MOTES_CONF = "conf/motes.json";
    public static final String APPS_CONF = "conf/apps.json";

    public static final String NETSERVER_LOG_PATH = "log/netserver/";
    public static final String APPSERVER_LOG_PATH = "log/appserver/";

    public static final int MAX_HANDLERS = 50;

    // Temporization of LoRaWAN downstream messages
    public static final long ONE_SECOND = 1000000; // microsec
    public static final long RECEIVE_DELAY1 = ONE_SECOND;
    public static final long RECEIVE_DELAY2 = RECEIVE_DELAY1 + ONE_SECOND;
    public static final long JOIN_ACCEPT_DELAY1 = 5000000; // microsec
    public static final long JOIN_ACCEPT_DELAY2 = JOIN_ACCEPT_DELAY1 + ONE_SECOND;

    public static final byte[] NET_ID = Hex.decode("000000");
    public static final int JOIN_ACCEPT_LENGTH = 12;
}
