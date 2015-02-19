package systems.obscure.servertestingwithouttor.client;

import android.os.StrictMode;

import com.squareup.wire.Message;
import com.squareup.wire.Wire;

import org.abstractj.kalium.crypto.SecretBox;
import org.abstractj.kalium.keys.KeyPair;
import org.abstractj.kalium.keys.PublicKey;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Created by charles on 2/16/15.
 */
public class Transport {
    // blockSize is the size of the blocks of data that we'll send and receive when
    // working in streaming mode. Each block is prefixed by two length bytes (which
    // aren't counted in blockSize) and includes secretbox.Overhead bytes of MAC
    // tag (which are).
    private final int blockSize = 4096 - 2;

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

    // readBuffer is used to receive bytes from the network when this Conn
    // is used to stream data.
    private byte[] readBuffer;
    // decryptBuffer is used to store decrypted payloads when this Conn is
    // used to stream data and the caller's buffer isn't large enough to
    // decrypt into directly.
    private byte[] decryptBuffer;
    // readPending aliases into decryptBuffer when a partial decryption had
    // to be returned to a caller because of buffer size limitations.
    private byte[] readPending;

    // writeBuffer is used to hold encrypted payloads when this Conn is
    // used for streaming data.
    private byte[] writeBuffer;

    private KeyPair ephemeralKeyPair;
    private byte[] ephemeralShared = new byte[32];

    private byte[] serverKeysMagic = "server keys\\x00".getBytes();
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

    public int Write(byte[] buf) throws IOException {
        int n = 0;
        if(writeBuffer == null)
            writeBuffer = new byte[blockSize];

        while(buf.length > 0) {
            int m = buf.length;
            if( m > blockSize - Constants.SECRETBOX_OVERHEAD)
                m = blockSize - Constants.SECRETBOX_OVERHEAD;
            byte[] cipherText = writeBox.encrypt(writeSequence, Arrays.copyOf(buf, m));
            int l = cipherText.length;
            for(int i= 0; i<l; i++)
                writeBuffer[i+2] = cipherText[i];
            writeBuffer[0] = (byte)l;
            writeBuffer[1] = (byte)(l >> 8);
            writer.write(writeBuffer, 0, l+2);
            n += m;
            buf = Arrays.copyOfRange(buf, m, buf.length);
            incSequence(writeSequence);
        }
        return  n;
    }

    public int Read(byte[] out) throws IOException {
        if(readPending == null || readPending.length > 0) {
            out = Arrays.copyOf(readPending, out.length);
            readPending = Arrays.copyOfRange(readPending, out.length, readPending.length);
            return out.length;
        }

        if(readBuffer == null)
            readBuffer = new byte[blockSize+2];

        reader.read(readBuffer, 0, 2);
        int n = (int)readBuffer[0] | (int)(readBuffer[1] << 8);
        if(n > readBuffer.length)
            throw new IOException("transport: peer's message too large for Read");

        reader.read(readBuffer, 0, n);

        try {
            if (out.length >= n - Constants.SECRETBOX_OVERHEAD) {
                // We can decrypt directly into the output buffer.
                out = readBox.decrypt(readSequence, readBuffer);
                n = out.length;
            } else {
                // We need to decrypt into a side buffer and copy a prefix of
                // the result into the caller's buffer.
                decryptBuffer = readBox.decrypt(readSequence, readBuffer);
                out = Arrays.copyOf(decryptBuffer, n);
                n = out.length;
                readPending = Arrays.copyOfRange(decryptBuffer, n, decryptBuffer.length);
            }
        }catch (RuntimeException e) {
            readPending = new byte[0];
            throw new IOException("transport: bad MAC");
        }
        incSequence(readSequence);
        return n;
    }

    public void writeProto(Message message) throws IOException {
        byte[] data = message.toByteArray();

        if(data.length > Constants.TRANSPORT_SIZE)
            throw new IOException("transport: message too large");

        byte[] buf = new byte[Constants.TRANSPORT_SIZE+2];
        buf[0] = (byte) data.length;
        buf[1] = (byte) (data.length >> 8);
        for (int i = 0; i < data.length; i++){
            buf[i+2] = data[i];
        }
        write(buf);
    }

    public Message readProto(Class proto) throws IOException {
        byte[] buf = new byte[Constants.TRANSPORT_SIZE+2+Constants.SECRETBOX_OVERHEAD];
        int n = read(buf);
        if(n != Constants.TRANSPORT_SIZE+2)
            throw new IOException("transport: message wrong length");

        n = (int)buf[0] + ((int)buf[1]) << 8;

        byte[] data = Arrays.copyOfRange(buf, 2, buf.length);
        if(n > data.length)
            throw new IOException("transport: corrupt message");
        Wire wire = new Wire();
        return wire.parseFrom(data, proto);
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
            byte[] theirData = Arrays.copyOf(data, theirLen);
            reader.read(theirData);
            byte[] plain = decrypt(theirData);
            data = Arrays.copyOf(plain, plain.length);
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

    private void setUpKeys(byte[] ephemeralShared) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA256");
            digest.update(clientKeysMagic);
            digest.update(ephemeralShared);
            writeKey = digest.digest();
            writeKeyValid = true;

            digest.reset();
            digest.update(serverKeysMagic);
            digest.update(ephemeralShared);
            readKey = digest.digest();
            readKeyValid = true;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
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
