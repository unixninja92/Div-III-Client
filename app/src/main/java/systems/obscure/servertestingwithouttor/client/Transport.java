package systems.obscure.servertestingwithouttor.client;

import android.os.StrictMode;

import org.abstractj.kalium.crypto.SecretBox;
import org.abstractj.kalium.keys.KeyPair;
import org.abstractj.kalium.keys.PublicKey;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Created by charles on 2/16/15.
 */
public class Transport {
    private Socket serverSocket;

    private BufferedInputStream reader;
    private BufferedOutputStream writer;

    private KeyPair identity;

    private PublicKey serverIdentity;

    private byte[] writeKey = new byte[32];
    private byte[] readKey = new byte[32];
    private SecretBox writeBox;
    private SecretBox readBox;
    private boolean writeKeyValid;
    private boolean readKeyValid;

    private byte[] writeSequence = new byte[24];
    private byte[] readSequence = new byte[24];

    private KeyPair ephemeralKeyPair;
    private byte[] ephemeralShared = new byte[32];

    private byte[] clientKeysMagic = "client keys\\x00".getBytes();//TODO set charset(utf-8)
    private byte[] clientProofMagic = "client proof\\x00".getBytes();

    String address = "whirlpool.obscure.systems";

    public Transport(KeyPair i, PublicKey server) {
        identity = i;
        serverIdentity = server;

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        serverSocket = new Socket();
        try {
            InetSocketAddress serverAddress = new InetSocketAddress(Inet4Address.getByName(address), 16333);
            serverSocket.connect(serverAddress);
            reader = new BufferedInputStream(serverSocket.getInputStream());
            writer = new BufferedOutputStream(serverSocket.getOutputStream());


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void Close() throws IOException {
        write(null);
        reader.close();
        writer.close();
        serverSocket.close();
    }

    private int write(byte[] data) {
        byte[] enc = encrypt(data);
        byte[] lenBytes = new byte[2];
        lenBytes[0] = (byte)enc.length;
        lenBytes[1] = (byte)(enc.length >> 8);
        try {
            writer.write(lenBytes);
            writer.write(enc);
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
        return data.length;
    }

    private int read(byte[] data) {
        byte[] lenBytes = new byte[2];
        try {
            reader.read(lenBytes);
            int theirLen = (int)lenBytes[1] + (int)lenBytes[2]<<8;
            if(theirLen > data.length)
                throw new IOException("tranport: given buffer too small ("+data.length+" vs "+theirLen+")");
            byte[] theirData = new byte[theirLen];
            for(int i = 0; i < theirLen; i++)
                theirData[i] = data[i];
            reader.read(theirData);
            byte[] plain = decrypt(theirData);
            for(int i = 0; i < plain.length; i++)
                data[i] = plain[i];
            return plain.length;
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
    }

    private byte[] encrypt(byte[] data) {
        if(!writeKeyValid)
            return data;
        byte[] enc = writeBox.encrypt(writeSequence, data);
        incSequence(writeSequence);
        return enc;
    }

    private byte[] decrypt(byte[] data) {
        if(!readKeyValid)
            return data;
        byte[] plain = readBox.decrypt(readSequence, data);
        incSequence(readSequence);
        //TODO check MAC??
        return plain;
    }

    public void handshake() {

    }

    private void handshakeClient() {

    }

    private byte[] incSequence(byte[] seq) {
        int n = 1;

        for(int i = 0; i<8; i++){
            n += (int)seq[i];
            seq[i] = (byte)n;
            n >>= 8;
        }
        return seq;
    }
}
