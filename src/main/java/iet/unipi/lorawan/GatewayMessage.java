package iet.unipi.lorawan;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;


/**
 * Implements Semtech Gateway Message Protocol
 */

public class GatewayMessage {
    public static final int MAX_LENGTH = 2400;

    // GWMP protocol version
    public static final byte GWMP_V1 = 0x01;
    public static final byte GWMP_V2 = 0x02;

    // GWMP message type
    public static final byte PUSH_DATA = 0x00;
    public static final byte PUSH_ACK = 0x01;
    public static final byte PULL_DATA = 0x02;
    public static final byte PULL_ACK = 0x04;
    public static final byte PULL_RESP = 0x03;
    public static final byte TX_ACK = 0x05;

    // Fields of a GWMP message
    public final byte version;
    public final short token;
    public final byte type;
    public final byte[] gateway;
    public final String payload;


    /**
     * Constructor which parses incoming data from socket
     * @param message Incoming data from UDP socket
     */

    public GatewayMessage(byte[] message) {
        ByteBuffer bb = ByteBuffer.wrap(message);
        bb.order(ByteOrder.BIG_ENDIAN);
        this.version = bb.get();
        this.token = bb.getShort();
        this.type = bb.get();

        int startPayload = 4;
        this.gateway = new byte[8];

        if (type != PUSH_ACK && type != TX_ACK) {
            bb.get(this.gateway,0,8);
            startPayload += 8;
        }

        if (type == PUSH_DATA || type == PULL_RESP || type == TX_ACK) {
            this.payload = (new String(Arrays.copyOfRange(message,startPayload,message.length), StandardCharsets.US_ASCII)).trim();
        } else {
            this.payload = null;
        }
    }


    /**
     * Constructor used to build a GWMP message from scratch
     * @param version GWMP version
     * @param token 16-bit token
     * @param type Type of GWMP message
     * @param gateway 64-bit Gateway EUI
     * @param payload JSON object containg BASE64 encoded LoRa frames and other informations
     */

    public GatewayMessage(byte version, short token, byte type, byte[] gateway, String payload) {
        this.version = version;
        this.token = token;
        this.type = type;
        this.gateway = gateway;
        this.payload = payload;
    }

    /**
     *
     * @param imme if true data will be sent immediately
     * @param tmst timestamp at which send data
     * @param freq center frequency
     * @param rfch antenna on which send data
     * @param powe tx power in dB
     * @param modu "LORA" or "FSK"
     * @param datr data rate
     * @param codr code rate
     * @param ipol if true, invert polarity
     * @param data frame to be sent
     * @param ncrc if not false, disable crc
     * @return
     */

    public static String getTxpk(
            boolean imme, long tmst, double freq, int rfch, int powe, String modu, String datr, String codr, boolean ipol, byte[] data, boolean ncrc) {
        JSONObject txpk = new JSONObject();

        if (imme) {
            txpk.put("imme",imme);
        } else {
            txpk.put("tmst",tmst);
        }

        txpk.put("freq",freq);
        txpk.put("rfch",rfch);
        txpk.put("powe",powe);
        txpk.put("modu",modu);
        txpk.put("datr",datr);
        txpk.put("codr",codr);
        txpk.put("ipol",ipol);
        txpk.put("size",data.length);
        String b64Data = Base64.getEncoder().encodeToString(data);
        txpk.put("data",b64Data);
        txpk.put("ncrc",ncrc);

        JSONObject payload = new JSONObject();
        payload.put("txpk",txpk);

        //System.out.println(payload.toString());
        return payload.toString().trim();
    }


    /**
     * Serialize GWMP packet
     * @return Serialized GWMP message as a byte array
     */

    public byte[] getBytes() {
        ByteBuffer bb = ByteBuffer.allocate(MAX_LENGTH);
        bb.order(ByteOrder.BIG_ENDIAN);
        bb.put(version);
        bb.putShort(token);
        bb.put(type);
        int msgLen = 4;

        if (type != PUSH_ACK && type != TX_ACK && type != PULL_RESP) {
            bb.put(gateway);
            msgLen += 8;
        }

        if (type == PUSH_DATA || type == PULL_RESP || type == TX_ACK) {
            byte[] payloadArray = payload.getBytes(StandardCharsets.US_ASCII);
            bb.put(payloadArray);
            msgLen += payloadArray.length;
        }

        return Arrays.copyOfRange(bb.array(),0,msgLen);
    }

    /**
     * Build UDP packet from GWMP message
     * @param gateway
     * @return
     */

    public DatagramPacket getPacket(InetSocketAddress gateway) {
        byte[] buff = this.getBytes();
        return new DatagramPacket(buff, buff.length, gateway.getAddress(), gateway.getPort());
    }
}
