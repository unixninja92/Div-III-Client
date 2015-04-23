package systems.obscure.client.client;

import android.os.StrictMode;

import com.google.common.io.ByteStreams;
import com.google.protobuf.Message;

import org.abstractj.kalium.crypto.Point;
import org.abstractj.kalium.crypto.SecretBox;
import org.abstractj.kalium.keys.KeyPair;
import org.abstractj.kalium.keys.PrivateKey;
import org.abstractj.kalium.keys.PublicKey;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import info.guardianproject.onionkit.proxy.SocksSocketFactory;
import systems.obscure.client.Globals;
import systems.obscure.client.protos.Pond;

/**
 * @author unixninja92
 */
public class Transport {
    // blockSize is the size of the blocks of data that we'll send and receive when
    // working in streaming mode. Each block is prefixed by two length bytes (which
    // aren't counted in blockSize) and includes secretbox.Overhead bytes of MAC
    // tag (which are).
    private final int blockSize = 4096 - 4;

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
    private String torAddress = "hv2pzxsx2drsyckk.onion";
//    private String torAddress = "vx652n4utsodj5c6.onion";

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

        try {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);

            SocksSocketFactory s = SocksSocketFactory.getSocketFactory("127.0.0.1", 9050);
            serverSocket = s.connectSocket(torAddress, 16333);

            reader = new BufferedInputStream(serverSocket.getInputStream());
            writer = serverSocket.getOutputStream();
            System.out.println(serverSocket.isConnected());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

//    private byte[] createMagic(byte[] string){
////        byte b = 0x00;
//        byte[] newBytes = new byte[string.length+1];
//        for(int i = 0; i<string.length; i++)
//            newBytes[i] = string[i];
//        newBytes[string.length] = 0x00;
//        return newBytes;
//    }

    public int Write(byte[] buf) throws IOException {
        int n = 0;
        if(writeBuffer == null)
            writeBuffer = new byte[blockSize];

        while(buf.length > 0) {
            int m = buf.length;
            if( m > blockSize - Globals.SECRETBOX_OVERHEAD)
                m = blockSize - Globals.SECRETBOX_OVERHEAD;
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
        if(readPending != null || readPending.length > 0) {
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
            if (out.length >= n - Globals.SECRETBOX_OVERHEAD) {
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

    public void writeProto(Message.Builder message) throws IOException {
        byte[] data = message.build().toByteArray();

        if(data.length > Globals.TRANSPORT_SIZE)
            throw new IOException("transport: message too large");

        byte[] b = new byte[Globals.TRANSPORT_SIZE+4];
        ByteBuffer buf = ByteBuffer.wrap(b);
        buf.putInt(data.length);
//        buf[0] = (byte) data.length;
//        buf[1] = (byte) (data.length >> 8);
//        buf[2] = (byte) (data.length >> 12);
//        buf[3] = (byte) (data.length >> 16);
//        for (int i = 0; i < data.length; i++){
//            buf[i+4] = data[i];
//        }
        buf.put(data);
        write(buf.array());
    }

    public Pond.Reply readProto() throws IOException {
        byte[] buf = new byte[Globals.TRANSPORT_SIZE+4+Globals.SECRETBOX_OVERHEAD];
        buf = read(buf);
        int n = buf.length;
        if(n != Globals.TRANSPORT_SIZE+4)
            throw new IOException("transport: message wrong length");

        n = ByteBuffer.wrap(buf).getInt();

        byte[] data = Arrays.copyOfRange(buf, 4, buf.length);
        if(n > data.length)
            throw new IOException("transport: corrupt message");

        data = Arrays.copyOfRange(data, 0, n);

        return Pond.Reply.parseFrom(data);
    }

    public void Close() {
        try {
            writer.write(0);
            reader.close();
            writer.close();
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int write(byte[] data) {
        byte[] enc = encrypt(data);
        ByteBuffer len = ByteBuffer.allocate(4);
        len.putInt(enc.length);
//        byte[] lenBytes = new byte[4];
//        lenBytes[0] = (byte)enc.length;
//        lenBytes[1] = (byte)(enc.length >> 8);
//        lenBytes[2] = (byte)(enc.length >> 12);
//        lenBytes[3] = (byte)(enc.length >> 16);
//        System.out.println(enc.length+" vs len[3] = "+ (int)lenBytes[3]+",len[2] = "+ (int)lenBytes[2]+",len[1] = "+ (int)lenBytes[1]+", len[0] = "+ (int)lenBytes[0]);
        try {
            writer.write(len.array());
            writer.write(enc);
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
        return data.length;
    }

    private byte[] read(byte[] data) {
        byte[] lenBytes = new byte[4];
        try {
            ByteStreams.readFully(reader, lenBytes);
//            System.out.println("len[3] = "+ (int)lenBytes[3]+",len[2] = "+ (int)lenBytes[2]+",len[1] = "+ (int)lenBytes[1]+", len[0] = "+ (int)lenBytes[0]);
            int theirLen = ByteBuffer.wrap(lenBytes).getInt();
//            long theirLen = (long)lenBytes[0] + (((long)lenBytes[1])<<8) + (((long)lenBytes[2])<<12)
//                    + (((int)lenBytes[3])<<16);
//            System.out.println(theirLen+" is len[3] = "+ (int)lenBytes[3]+",len[2] = "+ (int)lenBytes[2]+",len[1] = "+ (int)lenBytes[1]+", len[0] = "+ (int)lenBytes[0]);
            if(theirLen > data.length)
                throw new IOException("transport: given buffer too small ("+data.length+" vs "+theirLen+")");
            byte[] theirData = Arrays.copyOf(data, (int)theirLen);
//            printBytes(theirData);
            ByteStreams.readFully(reader, theirData);
            System.out.println("Read data");
            byte[] plain = decrypt(theirData);
//            printBytes(plain);
//            System.out.println("plain len: "+plain.length);
//            if(plain.length != theirData.length)
//                throw new IOException(shortMessageError);
//            data = Arrays.copyOf(plain, plain.length);
            return plain;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public byte readByte() throws IOException {
        byte[] b = new byte[1];
        ByteStreams.readFully(reader, b);
        return b[0];
    }

    private byte[] encrypt(byte[] data) {
        if(!writeKeyValid)
            return data;
//        System.out.println("Encrypt len: "+data.length);
        byte[] enc = writeBox.encrypt(writeSequence, data);
        incSequence(writeSequence);
        return enc;
    }

    private byte[] decrypt(byte[] data) {
//        System.out.println("Decrypt len: "+data.length);
        if(!readKeyValid)
            return data;
        byte[] plain = readBox.decrypt(readSequence, data);
        incSequence(readSequence);
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
//        System.out.println(Curve25519.isNative());
//            Curve25519 curve25519 =  Curve25519.getInstance(Curve25519.BEST);
            Point theirPoint = new Point(theirEphemeralPublicKey);
            ephemeralShared = theirPoint.mult(ephemeralKeyPair.getPrivateKey().toBytes()).toBytes();
//            ephemeralShared = curve25519.calculateAgreement(theirEphemeralPublicKey, ephemeralKeyPair.getPrivateKey().toBytes());
            printBytes(ephemeralShared);

//            write(ephemeralKeyPair.getPublicKey().toBytes());
            setUpKeys(ephemeralShared);
//            handshakeClient(handshakeHash, ephemeralKeyPair.getPrivateKey());
            PrivateKey ephemeralPrivate = ephemeralKeyPair.getPrivateKey();

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

                byte[] digestReceived = read(new byte[digest.length + Globals.SECRETBOX_OVERHEAD]);
//            int n = read(digestReceived);
//            if(n != digest.length)
//                throw new IOException(shortMessageError);

//            digestReceived = Arrays.copyOf(digestReceived, n);

                if(!MessageDigest.isEqual(digest, digestReceived))
                    throw new IOException("transport: server identity incorrect");

                byte[] identityShared = peerP.mult(identity.getPrivateKey().toBytes()).toBytes();
                Key identShared = new SecretKeySpec(identityShared, "HmacSHA256");
                printBytes(identityShared);

                handshakeHash.update(ephemeralKeyPair.getPublicKey().toBytes());
                handshakeHash.update(theirEphemeralPublicKey);
                handshakeHash.update(digest);
                digest = handshakeHash.digest();
//                System.out.print("mac of mac: ");
                printBytes(digest);

                h.reset();
                h.init(identShared);
                h.update(clientProofMagic);
                h.update(digest);

                byte[] finalMac = h.doFinal();
                printBytes(finalMac);
            printBytes(digest);
                byte[] pub = identity.getPublicKey().toBytes();
                byte[] finalMessage = new byte[pub.length+finalMac.length];//pubkey + sha256
                for(int i = 0; i < pub.length; i++)
                    finalMessage[i] = pub[i];
                for(int i = 0; i < finalMac.length; i++)
                    finalMessage[pub.length+i] = finalMac[i];
//            h.doFinal(finalMessage, 32);

//                System.out.println("We did the thing!");
                write(finalMessage);
//                System.out.println("We sent the thing!");
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
//        } catch (ShortBufferException e) {
//            e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            }

        } catch (NoSuchAlgorithmException e) {
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
