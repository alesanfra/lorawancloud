package iet.unipi.Lora.NetworkServer;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Created by alessio on 16/03/16.
 */
public class FrameMessage {

    public static final int HEADER_LEN = 7;
    public static final int UPSTREAM = 0;
    public static final int DOWNSTREAM = 1;

    // Frame Control flags
    public static final byte ACK = 0x20;

    public int devAddress;
    public byte control;
    public short counter;
    public int optLen;
    public byte[] options;
    public byte port;
    public byte[] payload;
    public byte dir;

    public FrameMessage(int devAddress, byte control, short counter, byte[] options, int port, byte[] payload, int dir) {
        this.devAddress = devAddress;
        this.control = control;
        this.counter = counter;
        this.optLen = (options != null)? options.length : 0;
        this.options = options;
        this.port = (byte) port;
        this.payload = payload;
        this.dir = (byte) dir;
    }

    public FrameMessage (MACMessage macMessage) {
        this.dir = (byte) ((macMessage.type == MACMessage.CONFIRMED_DATA_UP || macMessage.type == MACMessage.UNCONFIRMED_DATA_UP || macMessage.type == MACMessage.JOIN_REQUEST) ? UPSTREAM : DOWNSTREAM);
        byte[] data = macMessage.payload;
        ByteBuffer bb = ByteBuffer.wrap(data);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        this.devAddress = bb.getInt(0);
        this.control = bb.get(4);
        this.counter = bb.getShort(5);
        this.optLen = control & 0xF;

        if (this.optLen > 0) {
            this.options = Arrays.copyOfRange(data, 7, 7+this.optLen);
        }
        
        if (data.length > 7+this.optLen) {
            // C'è il payload
            this.port = bb.get(7+this.optLen);
            this.payload = Arrays.copyOfRange(data, 8+this.optLen, data.length);
        }

        //System.out.println(String.format("Frame size: %d, optsize: %d, port %d, payload: %d", data.length, optionsSize, this.port, this.payload.length));
    }

    public byte[] getEncryptedPayload(byte[] key) {
        //System.out.println(Arrays.toString(key));
        int payloadSize =  this.payload.length;
        int targetSize = (payloadSize % 16 == 0)? payloadSize : ((payloadSize/16) + 1) * 16;
        //System.out.println("Size " + payloadSize + ", target: " +targetSize);

        ByteBuffer bb = ByteBuffer.allocate(targetSize);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        for (int i=0; i<targetSize; i+=16) {
            bb.put(i, (byte) 1);
            bb.put(i+1, (byte) 0);
            bb.put(i+2, (byte) 0);
            bb.put(i+3, (byte) 0);
            bb.put(i+4, (byte) 0);
            bb.put(i+5, this.dir);
            bb.putInt(i+6, this.devAddress);
            bb.putInt(i+10, (int) this.counter);
            bb.put(i+14, (byte) 0);
            bb.put(i+15, (byte) ((i/16)+1));
        }

        byte[] A = bb.array();
        //System.out.println(Arrays.toString(A));

        try {
            // Create key and cipher
            Key aesKey = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");

            // Create S
            cipher.init(Cipher.ENCRYPT_MODE, aesKey);
            byte[] S = cipher.doFinal(A);

            byte[] decrypted = new byte[payloadSize];

            for (int i=0; i<payloadSize; i++) {
                decrypted[i] = (byte) (this.payload[i] ^ S[i]);
            }

            return decrypted;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }

        return null;
    }

    // Convenience method (encryption == decryption)
    public byte[] getDecryptedPayload(byte[] key) {
        return this.getEncryptedPayload(key);
    }

    public int getAck() {
        return (this.control & 0x20) >> 5;
    }

}
