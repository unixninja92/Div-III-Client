package systems.obscure.client.ratchet;

import com.google.protobuf.ByteString;

import org.abstractj.kalium.crypto.Point;
import org.abstractj.kalium.keys.KeyPair;
import org.abstractj.kalium.keys.VerifyKey;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import systems.obscure.client.protos.Pond;

/**
 * @author unixninja92
 */
public class Ratchet {
    KeyPair myIdentity;
    KeyPair theirIdentity;
    VerifyKey myVerifyKey;
    VerifyKey theirVerifyKey;

    byte[] rootkey;
    byte[] sendHeaderKey;
    byte[] recvHeaderKey;
    byte[] nextSendHeaderKey;
    byte[] nextRecvHeaderKey;
    byte[] sendRatchetPrivate;
    byte[] recvRatchetPublic;
    long sendCount;
    long recvCount;
    long prevSendCount;

    boolean rachet;

    HashMap<byte[], HashMap<Long, SavedKey>> saved;

    byte[] kxPrivate0;
    byte[] kxPrivate1;

    SecureRandom rand;

    public Ratchet(SecureRandom rand) {
        this.rand = rand;
        kxPrivate0 = new byte[32];
        kxPrivate1 = new byte[32];
        rand.nextBytes(kxPrivate0);
        rand.nextBytes(kxPrivate1);
        saved = new HashMap<>();
    }

    public void fillKeyExchagne(Pond.KeyExchange.Builder kx) throws IOException {
        if (kxPrivate0 == null || kxPrivate1 == null) {
            throw new IOException("ratchet: handshake already complete"); //TODO find better exception to throw
        }
        KeyPair pair0 = new KeyPair(kxPrivate0);
        KeyPair pair1 = new KeyPair(kxPrivate1);

        kx.setDh(ByteString.copyFrom(pair0.getPublicKey().toBytes()));
        kx.setDh1(ByteString.copyFrom(pair1.getPublicKey().toBytes()));
    }

//    public byte[] deriveKey() TODO wtf??

    public byte[] getKXPrivateForTransition() {
        return kxPrivate0;
    }

    public void completeKeyExchange(Pond.KeyExchange kx) throws IOException {
        if(kxPrivate0 == null)
            throw new IOException("ratchet: handshake already complete");

        KeyPair pair = new KeyPair(kxPrivate0);

        ByteBuffer public0 = ByteBuffer.wrap(pair.getPublicKey().toBytes());

        if(kx.getDh().size() != public0.remaining())
            throw new IOException("ratchet: peer's key exchange is invalid");
        if(kx.getDh1().size() != public0.remaining())
            throw new IOException("ratchet: peer's key exchange is invalid");

        boolean amAlice = false;
        switch (public0.compareTo(ByteBuffer.wrap(kx.getDh().toByteArray()))){
            case -1:
                amAlice = true; break;
            case 1: amAlice = false; break;
            case 0:
                throw new IOException("ratchet: peer echoed our own DH values back");
        }

        Point theirDH = new Point(kx.getDh().toByteArray());

        Point sharedKey = theirDH.mult(kxPrivate0);

        ByteBuffer keyMaterial = ByteBuffer.allocate(32*5);
        keyMaterial.put(sharedKey.toBytes());

        Point theirIdentityPublic = new Point(theirIdentity.getPublicKey().toBytes());

        if(amAlice) {
            keyMaterial.put(theirDH.mult(myIdentity.getPrivateKey().toBytes()).toBytes());
            keyMaterial.put(theirIdentityPublic.mult(kxPrivate0).toBytes());
        }
        else {
            keyMaterial.put(theirIdentityPublic.mult(kxPrivate0).toBytes());
            keyMaterial.put(theirDH.mult(myIdentity.getPrivateKey().toBytes()).toBytes());
        }

        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(keyMaterial.array(), "HmacSHA256"));

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
    }

}
