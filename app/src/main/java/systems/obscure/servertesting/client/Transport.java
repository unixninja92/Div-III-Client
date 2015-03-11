package systems.obscure.servertesting.client;

import android.os.StrictMode;

import com.google.common.io.ByteStreams;
import com.squareup.wire.Message;
import com.squareup.wire.Wire;

import org.abstractj.kalium.crypto.Point;
import org.abstractj.kalium.crypto.SecretBox;
import org.abstractj.kalium.keys.KeyPair;
import org.abstractj.kalium.keys.PrivateKey;
import org.abstractj.kalium.keys.PublicKey;
import org.whispersystems.curve25519.Curve25519;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;

//import org.abstractj.kalium.c

/**
 * @author unixninja92
 */
public class Transport {
    // blockSize is the size of the blocks of data that we'll send and receive when
    // working in streaming mode. Each block is prefixed by two length bytes (which
    // aren't counted in blockSize) and includes secretbox.Overhead bytes of MAC
    // tag (which are).
    private final int blockSize = 4096 - 2;

    private Socket serverSocket;

    private BufferedInputStream reader;
    private OutputStream writer;

    private KeyPair identity;

    private PublicKey serverIdentity;

    public byte[] peer;

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

    private byte[] serverKeysMagic;
    private byte[] clientKeysMagic;

    private byte[] serverProofMagic;
    private byte[] clientProofMagic;

    private String address = "zkpp.obscure.systems";

    private String shortMessageError = "transport: received short handshake message";

    public Transport(KeyPair i, PublicKey server) {
        identity = i;
        serverIdentity = server;
        peer = serverIdentity.toBytes();



        try {
            byte b = 0x00;
            serverKeysMagic = ("server keys snap").getBytes("UTF-8");
            clientKeysMagic = ("client keys snap").getBytes("UTF-8");

            serverProofMagic = ("server proof snap").getBytes("UTF-8");//createMagic(("server proof"+b).getBytes("UTF-8"));
            clientProofMagic = ("client proof snap").getBytes("UTF-8");//createMagic(("client proof"+b).getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        serverSocket = new Socket();
        try {
//            InetSocketAddress serverAddress = new InetSocketAddress(Inet4Address.getByAddress(new byte[] { (byte)172, (byte)31, (byte)174, (byte)213 }), 16333);//getByName("172.31.174.213")
            InetSocketAddress serverAddress = new InetSocketAddress(Inet4Address.getByName(address), 16333);
//            System.out.println("Test 1");
            serverSocket.connect(serverAddress);
            reader = new BufferedInputStream(serverSocket.getInputStream());
            writer = serverSocket.getOutputStream();
            System.out.println(serverSocket.isConnected());


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private byte[] createMagic(byte[] string){
//        byte b = 0x00;
        byte[] newBytes = new byte[string.length+1];
        for(int i = 0; i<string.length; i++)
            newBytes[i] = string[i];
        newBytes[string.length] = 0x00;
        return newBytes;
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

        ByteStreams.readFully(reader, readBuffer, 0, 2);
        int n = (int)readBuffer[0] | (((int)readBuffer[1])<<8);
        if(n > readBuffer.length)
            throw new IOException("transport: peer's message too large for Read");

        ByteStreams.readFully(reader, readBuffer, 0, n);

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
        buf = read(buf);
        int n = buf.length;
        if(n != Constants.TRANSPORT_SIZE+2)
            throw new IOException("transport: message wrong length");

        n = (int)buf[0] + (((int)buf[1])<<8);

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
        System.out.println("write len[1] = "+ (int)lenBytes[1]+", len[0] = "+ (int)lenBytes[0]);
        try {
            writer.write(lenBytes);
            writer.write(enc);
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
        return data.length;
    }

    private byte[] read(byte[] data) {
        byte[] lenBytes = new byte[2];
        try {
            ByteStreams.readFully(reader, lenBytes);
//            reader.read(lenBytes);
            System.out.println("len[1] = "+ (int)lenBytes[1]+", len[0] = "+ (int)lenBytes[0]);
            int theirLen = (int)lenBytes[0] + (((int)lenBytes[1])<<8);
            if(theirLen > data.length)
                throw new IOException("transport: given buffer too small ("+data.length+" vs "+theirLen+")");
            byte[] theirData = Arrays.copyOf(data, theirLen);
//            reader.read(theirData);
//            printBytes(theirData);
            ByteStreams.readFully(reader, theirData);
            byte[] plain = decrypt(theirData);
//            printBytes(plain);
            System.out.println("plain len: "+plain.length);
//            if(plain.length != theirData.length)
//                throw new IOException(shortMessageError);
//            data = Arrays.copyOf(plain, plain.length);
            return plain;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private byte[] encrypt(byte[] data) {
        if(!writeKeyValid)
            return data;
        System.out.println("Encrypt len: "+data.length);
        byte[] enc = writeBox.encrypt(writeSequence, data);
        incSequence(writeSequence);
        return enc;
    }

    private byte[] decrypt(byte[] data) {
        System.out.println("Decrypt len: "+data.length);
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
            writeBox = new SecretBox(writeKey);

            digest.reset();
            digest.update(serverKeysMagic);
            digest.update(ephemeralShared);
            readKey = digest.digest();
            readKeyValid = true;
            readBox = new SecretBox(readKey);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    public void handshake() throws IOException {
        ephemeralKeyPair = new KeyPair();
        printBytes(ephemeralKeyPair.getPublicKey().toBytes());
        write(ephemeralKeyPair.getPublicKey().toBytes());

        byte[] theirEphemeralPublicKey = read(new byte[32]);
//        int n = theirEphemeralPublicKey);
//        if(n != theirEphemeralPublicKey.length)
//            throw new IOException(shortMessageError);
        printBytes(theirEphemeralPublicKey);


        try {
            MessageDigest handshakeHash = MessageDigest.getInstance("SHA256");
            handshakeHash.update(ephemeralKeyPair.getPublicKey().toBytes());
            handshakeHash.update(theirEphemeralPublicKey);
        System.out.println(Curve25519.isNative());

            Point theirPoint = new Point(theirEphemeralPublicKey);
            ephemeralShared = theirPoint.mult(ephemeralKeyPair.getPrivateKey().toBytes()).toBytes();
            printBytes(ephemeralShared);

//            write(ephemeralKeyPair.getPublicKey().toBytes());
            setUpKeys(ephemeralShared);
            handshakeClient(handshakeHash, ephemeralKeyPair.getPrivateKey());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    private void handshakeClient(MessageDigest handshakeHash, PrivateKey ephemeralPrivate) throws IOException {
        Point peerP = new Point(peer);
        byte[] ephemeralIdentityShared = peerP.mult(ephemeralPrivate.toBytes()).toBytes();
        Key ephemIdentShared = new SecretKeySpec(ephemeralIdentityShared, "HmacSHA256");
        printBytes(ephemeralIdentityShared);
        byte[] digest = handshakeHash.digest();
        try {
            Mac h = Mac.getInstance("HmacSHA256");
            h.init(ephemIdentShared);
            h.update(serverProofMagic);
            h.update(digest);
            digest = h.doFinal();

            byte[] digestReceived = read(new byte[digest.length + Constants.SECRETBOX_OVERHEAD]);
//            int n = read(digestReceived);
//            if(n != digest.length)
//                throw new IOException(shortMessageError);

//            digestReceived = Arrays.copyOf(digestReceived, n);

            if(!MessageDigest.isEqual(digest, digestReceived))
                throw new IOException("transport: server identity incorrect");

            byte[] identityShared = peerP.mult(identity.getPrivateKey().toBytes()).toBytes();
            Key identShared = new SecretKeySpec(identityShared, "HmacSHA256");
            printBytes(identityShared);
            handshakeHash.update(digest);
            digest = handshakeHash.digest();

            h.init(identShared);
            h.update(clientProofMagic);
            h.update(digest);

            byte[] finalMessage = new byte[32+32];//pubkey + sha256
            byte[] pub = identity.getPublicKey().toBytes();
            for(int i = 0; i < pub.length; i++)
                finalMessage[i] = pub[i];
            h.doFinal(finalMessage, 32);

            System.out.println("We did the thing!");
            write(finalMessage);
            System.out.println("We sent the thing!");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (ShortBufferException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
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

    private void printBytes(byte[] b) {
        for(int i = 0; i < b.length; i++)
            System.out.print((b[i] & 0xFF)+",");
        System.out.println();
    }
}
