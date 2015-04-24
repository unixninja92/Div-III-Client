package systems.obscure.client.disk;

import com.google.common.io.Files;
import com.google.protobuf.ByteString;

import org.abstractj.kalium.crypto.SecretBox;
import org.jcsp.lang.AltingChannelInput;
import org.jcsp.lang.ChannelOutput;
import org.jcsp.lang.PoisonException;
import org.spongycastle.crypto.generators.SCrypt;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.KeyException;
import java.security.SecureRandom;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import systems.obscure.client.protos.LocalStorage;

/**
 * @author unixninja92
 */
public class StateFile{
    public static final int kdfSaltLen = 32;
    public static final int kdfKeyLen = 32;
    public static final int erasureKeyLen = 32;

    private static final int Default_Header_SCrypt_N = 32768;
    private static final int Default_Header_SCrypt_R = 16;
    private static final int Default_Header_SCrypt_P = 1;

    String path;
    SecureRandom rand;

    // Erasure is able to store a `mask key' - a random value that is XORed
    // with the key. This is done because an ErasureStorage is believed to
    // be able to erase old mask values.
    ErasureStorage erasureStorage;

    LocalStorage.Header header;
    byte[] key = new byte[kdfKeyLen];
    byte[] mask = new byte[erasureKeyLen];
    boolean valid = false;

    private final byte[] headerMagic = {(byte)0xa8, (byte)0x34, (byte)0x64, (byte)0x9e,(byte) 0xce,
            (byte)0x39, (byte)0x94, (byte)0xe3};

    private final ReentrantReadWriteLock lock;

    private AltingChannelInput<NewState> input;
    private ChannelOutput output;

    public StateFile(SecureRandom r, String p) {
        rand = r;
        path = p;

        lock = new ReentrantReadWriteLock();
    }



    private void deriveKey(byte[] pw) throws KeyException {
        if(pw.length == 0 && header.getScrypt() == null)
            throw new KeyException("bad password");
        LocalStorage.Header.SCrypt prams;
//        if(header == null) {
//            systems.obscure.client.protos.LocalStorage.Header.SCrypt.Builder scrypt = systems.obscure.client.protos.LocalStorage.Header.SCrypt.newBuilder();
//            scrypt.setN(Default_Header_SCrypt_N);
//            scrypt.setP(Default_Header_SCrypt_P);
//            scrypt.setR(Default_Header_SCrypt_R);
//            prams = scrypt.build();
//        }
//        else
             prams = header.getScrypt();
        key = SCrypt.generate(pw, header.getKdfSalt().toByteArray(), prams.getN(),
                prams.getR(), prams.getP(), kdfKeyLen);
    }

    public void Create(byte[] pw) throws KeyException {
        byte[] salt = new byte[kdfSaltLen];
        rand.nextBytes(salt);
        LocalStorage.Header.Builder hBuilder = LocalStorage.Header.newBuilder();
        LocalStorage.Header.SCrypt.Builder scrypt = LocalStorage.Header.SCrypt.newBuilder();
        scrypt.setN(Default_Header_SCrypt_N);
        scrypt.setP(Default_Header_SCrypt_P);
        scrypt.setR(Default_Header_SCrypt_R);
        hBuilder.setScrypt(scrypt.build());

        if(pw.length > 0) {
            hBuilder.setKdfSalt(ByteString.copyFrom(salt));
            header = hBuilder.build();
            deriveKey(pw);
//            hBuilder.setScrypt(systems.obscure.client.protos.LocalStorage.Header.SCrypt.newBuilder());
        }
        hBuilder.setNoErasureStorage(true);
        header = hBuilder.build();
        valid = true;
    }

    public LocalStorage.State Read(byte[] pw) throws IOException {
        try {
            lock.readLock().lock();
            File stateFile = new File(path);
            ByteBuffer b = ByteBuffer.wrap(Files.toByteArray(stateFile));

            if(b.capacity() < headerMagic.length+4)
                throw new IOException("state file is too small to be valid");

            for(int i = 0; i < headerMagic.length; i++)
                if(b.get() != headerMagic[i])
                    throw new IOException("Header magic does not match");

            int headerLen = (int) b.get();

            System.out.println("header len: "+headerLen);

            if(headerLen > 1<<16)
                throw new IOException("state file corrupt");
            if(b.remaining() < headerLen)
                throw  new IOException("state file truncated");
            byte[] headerBytes = new byte[headerLen];
            b.get(headerBytes);

            header = LocalStorage.Header.parseFrom(headerBytes);

            if(pw.length > 0)
                deriveKey(pw);

//            if(!header.getNoErasureStorage()){
//
//            }

            int smearedCopies = header.getNonceSmearCopies();

            if(b.remaining() < 24*smearedCopies)
                throw new IOException("state file truncated");

            byte[] nonce = new byte[24];
            for(int i = 0; i < smearedCopies; i++)
                for(int j = 0; j < 24; j++)
                    nonce[j] ^= b.get(b.position()+(24*i+j));

            b.position(b.position() + (24 * smearedCopies));

            byte[] effectiveKey = new byte[kdfKeyLen];

            for(int i = 0; i < effectiveKey.length; i++) {
                effectiveKey[i] = (byte)(mask[i] ^ key[i]);
            }

            byte[] ciphertext = new byte[b.remaining()];
            b.get(ciphertext);

            SecretBox secretBox = new SecretBox(effectiveKey);
            ByteBuffer plaintext = ByteBuffer.wrap(secretBox.decrypt(nonce, ciphertext));
            if(plaintext.capacity() < 4)
                throw new IOException("state file corrupt");

            lock.readLock().unlock();

            int length = plaintext.getInt();
            if(length < 0 || length > plaintext.remaining())
                throw new IOException("state file corrupt");

            byte[] plain = new byte[length];
            plaintext.get(plain);

            System.out.println("Statefile read!");

            return LocalStorage.State.parseFrom(plain);
        } catch (KeyException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void StartWrtie(AltingChannelInput<NewState> in, ChannelOutput out) {
        input = in;
        output = out;
        run();
    }

    //poison channel for closing. Then catch poison exception to detect that closing
//    @Override
    public void run() {

        while(true) {
            NewState newState = null;
            try {
                newState = input.read();
                if(newState == null){
                    output.poison(10);
                    return;
                }
            }catch (PoisonException e){
                output.poison(10);
                return;
            }

            if(newState.Destruct) {
                System.out.println("disk: Destruct command received.");
                byte[] newMask = new byte[erasureKeyLen];
                rand.nextBytes(newMask);
                if(erasureStorage != null){
                    try {
                        erasureStorage.Write(key, newMask);
                    } catch (IOException e) {
                        System.out.print("disk: Error while clearing NVRAM: ");
                        e.printStackTrace();
                    }
                    erasureStorage.Destroy(key);
                }
                File out = new File(path);
                if(out.exists()){
                    long pos = out.length();
                    System.out.println("disk: writing "+pos+" zeros to statefile");
                    byte[] zeros = new byte[(int)pos];
                    try {
                        FileOutputStream fileOutputStream = new FileOutputStream(out);
                        synchronized (fileOutputStream) {
                            fileOutputStream.write(zeros);
                            fileOutputStream.flush();
                            fileOutputStream.close();
                        }
                        out.delete();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                output.poison(10);
                return;
            }

            byte[] s = newState.state;
            int length = s.length+4;
            for(int i = 17; i < 32; i++) {
                int n = 1 << i;
                if(n >= length) {
                    length = n;
                    break;
                }
            }

            byte[] plaintext = new byte[length];
            rand.nextBytes(plaintext);
            ByteBuffer plainbuffer = ByteBuffer.wrap(plaintext);
            plainbuffer.putInt(s.length);
            plainbuffer.put(s);

            int smearCopies = header.getNonceSmearCopies();

            byte[] nonceSmear = new byte[smearCopies*24];
            rand.nextBytes(nonceSmear);

            byte[] nonce = new byte[24];
            for(int i = 0; i < smearCopies; i++) {
                for(int j = 0; j < 24; j++) {
                    nonce[j] ^= nonceSmear[24*i+j];
                }
            }

//            if(erasureStorage != null && newState.RotateErasureStorage){
//
//            }
            byte[] effectiveKey = new byte[kdfKeyLen];

            for(int i = 0; i < effectiveKey.length; i++)
                effectiveKey[i] = (byte)(mask[i] ^ key[i]);

            SecretBox secretBox = new SecretBox(effectiveKey);
            byte[] ciphertext = secretBox.encrypt(nonce, plainbuffer.array());
            System.out.println("Encrypted the state!!!");
            try {
                File temp = new File(path+".tmp");
                FileOutputStream tempOut = new FileOutputStream(temp);
                tempOut.write(headerMagic);
                tempOut.write((byte) header.toByteArray().length);
                tempOut.write(header.toByteArray());
                tempOut.write(nonceSmear);
                tempOut.write(ciphertext);
                tempOut.flush();
                tempOut.close();
                temp.setReadOnly();

                File oldTemp = new File(path+"~");
                if(oldTemp.isFile())
                    oldTemp.delete();

                lock.writeLock().lock();
                File state = new File(path);
                File tempOldState = new File(path+"~");
                state.renameTo(tempOldState);

                temp.renameTo(state);

                System.out.println("Here be that new state files path: " + state.getPath());

                tempOldState.delete();

                lock.writeLock().unlock();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public ReentrantReadWriteLock getLock() {
        return lock;
    }

    private void printBytes(byte[] b, int offset, int len) {
        for(int i = offset; i < len; i++)
            System.out.print((b[i])+",");
        System.out.println();
    }

}
