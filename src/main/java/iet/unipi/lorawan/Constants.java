package iet.unipi.lorawan;


public class Constants {
    public static final int EUI_LENGTH = 16;
    public static final int NETSERVER_LISTENING_PORT = 55667;
    public static final String NETSERVER_ADDRESS = "localhost";

    public static final int GATEWAYS_LISTENING_PORT = 1700;
    public static final String MOTES_CONF = "conf/motes.json";

    // Temporization of LoRaWAN downstream messages
    public static final long RECEIVE_DELAY1 = 1000000; // microsec
    public static final long RECEIVE_DELAY2 = RECEIVE_DELAY1 + 1000000; // microsec
    public static final long JOIN_ACCEPT_DELAY1 = 5000000;
    public static final long JOIN_ACCEPT_DELAY2 = JOIN_ACCEPT_DELAY1 + 1000000;
}
