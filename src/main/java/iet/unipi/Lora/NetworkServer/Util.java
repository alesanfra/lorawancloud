package iet.unipi.Lora.NetworkServer;

/**
 * Created by alessio on 03/05/16.
 */
public class Util {
    /**
     * Format EUI in a readble way
     * @param eui EUI expressed as a number
     * @return EUI String like AA:BB:CC:DD:EE:FF:GG:HH
     */

    public static String formatEUI(byte[] eui) {
        StringBuilder sb = new StringBuilder(23);

        for (int i=0; i<8 && i<eui.length; i++) {
            if (sb.length() > 0) {
                sb.append(':');
            }
            sb.append(String.format("%02X",eui[i]));
        }
        return sb.toString().toUpperCase();
    }
}
