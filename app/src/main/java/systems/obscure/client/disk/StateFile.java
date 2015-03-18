package systems.obscure.client.disk;

import android.app.Activity;

import com.google.common.io.Files;
import com.google.protobuf.ByteString;

import org.abstractj.kalium.crypto.SecretBox;
import org.spongycastle.crypto.generators.SCrypt;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.KeyException;
import java.security.SecureRandom;

import systems.obscure.client.protos.LocalStorage;

/**
 * @author unixninja92
 */
public class StateFile extends Activity{
    String path;
    SecureRandom rand;

    // Erasure is able to store a `mask key' - a random value that is XORed
    // with the key. This is done because an ErasureStorage is believed to
    // be able to erase old mask values.
    ErasureStorage erasureStorage;

    LocalStorage.Header header;
    byte[] key = new byte[Constants.kdfKeyLen];
    byte[] mask = new byte[Constants.erasureKeyLen];
    boolean valid = false;

    private final byte[] headerMagic = {(byte)0xa8, (byte)0x34, (byte)0x64, (byte)0x9e,(byte) 0xce,
            (byte)0x39, (byte)0x94, (byte)0xe3};

//    private final ReentrantReadWriteLock lockFDMutex = new ReentrantReadWriteLock();
//     int lockFD;

    public StateFile(SecureRandom r, String p) {
        rand = r;
        path = p;
    }



    public void deriveKey(String pw) throws KeyException {
        if(pw.length() == 0 && header.hasScrypt())
            throw new KeyException("bad password");
        LocalStorage.Header.SCrypt prams = header.getScrypt();
        key = SCrypt.generate(pw.getBytes(), header.getKdfSalt().toByteArray(), prams.getN(),
                prams.getR(), prams.getP(), Constants.kdfKeyLen);
    }

    public void Create(String pw) throws KeyException {
        byte[] salt = new byte[Constants.kdfSaltLen];
        rand.nextBytes(salt);
        LocalStorage.Header.Builder hBuilder = LocalStorage.Header.newBuilder();
        if(pw.length() > 0) {
            hBuilder.setKdfSalt(ByteString.copyFrom(salt));
            deriveKey(pw);
            hBuilder.setScrypt(LocalStorage.Header.SCrypt.newBuilder());
        }
        hBuilder.setNoErasureStorage(true);
        header = hBuilder.build();
        valid = true;
    }

    public LocalStorage.State Read(String pw) throws IOException {
        try {
            File stateFile = new File(getFilesDir(), "state_file");
            ByteBuffer b = ByteBuffer.wrap(Files.toByteArray(stateFile));

            if(b.capacity() < headerMagic.length+4)
                throw new IOException("state file is too small to be valid");

            b.position(headerMagic.length);
            int headerLen = b.getInt();

            if(headerLen > 1<<16)
                throw new IOException("state file corrupt");
            if(b.remaining() < headerLen)
                throw  new IOException("state file truncated");
            byte[] headerBytes = new byte[headerLen];
            b.get(headerBytes);

            header = LocalStorage.Header.parseFrom(headerBytes);

            if(pw.length() > 0)
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
                    nonce[j] ^= b.get(24*i+j);

            b.position(b.position()+24*smearedCopies);

            byte[] effectiveKey = new byte[Constants.kdfKeyLen];

            for(int i = 0; i < effectiveKey.length; i++) {
                effectiveKey[i] = (byte)(mask[i] ^ key[i]);
            }

            SecretBox secretBox = new SecretBox(effectiveKey);
            ByteBuffer plaintext = ByteBuffer.wrap(secretBox.decrypt(nonce, b.slice().array()));
            if(plaintext.capacity() < 4)
                throw new IOException("state file corrupt");

            int length = plaintext.getInt();
            if(length > 1<<31 || length > plaintext.remaining())
                throw new IOException("state file corrupt");

            byte[] plain = new byte[length];
            plaintext.get(plain);

            return LocalStorage.State.parseFrom(plain);
        } catch (KeyException e) {
            e.printStackTrace();
        }
        return null;
    }


}
