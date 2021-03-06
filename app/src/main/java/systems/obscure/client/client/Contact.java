package systems.obscure.client.client;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.abstractj.kalium.keys.PublicKey;
import org.abstractj.kalium.keys.VerifyKey;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import javax.crypto.Mac;

import systems.obscure.client.crypto.SigningKey;
import systems.obscure.client.protos.Pond;
import systems.obscure.client.ratchet.Ratchet;

/**
 * @author unixninja92
 */
public class Contact {

    // hmacValueMask is the bottom 63 bits. This is used for HMAC values
    // where the HMAC is only 63 bits wide and the MSB is used to signal
    // whether a revocation was used or not.
    public final long hmacValueMask = 0x7fffffffffffffffL;
    // id is only locally valid.
    public long id;

    // name is the friendly name that the user chose for this contact. It
    // is unique for all contacts.
    public String name;

    // isPending is true if we haven't received a key exchange message from
    // this contact.
    boolean isPending = true;

    // kxsBytes is the serialised key exchange message that we generated
    // for this contact. (Only valid if |isPending| is true.)
    public byte[] kxsBytes;



    // theirServer is the URL of the contact's home server.
    public String theirServer;

    // theirPub is their Ed25519 public key.
    public VerifyKey theirPub;

    // theirIdentityPublic is the public identity that their home server
    // knows them by.
    public PublicKey theirIdentityPublic;

    // supportedVersion contains the greatest protocol version number that
    // we have observed from this contact.
    public int supportedVersion;

    // revoked is true if this contact has been revoked.
    public boolean revoked = false;

    // revokedUs is true if this contact has revoked us.
    public boolean revokedUs = false;

    public ArrayList<Pond.HMACPair> theirHMACPairs = new ArrayList<>();

    public ArrayList<HMACPair> myHMACs = new ArrayList<>();

    public Ratchet ratchet;

//    // pandaKeyExchange contains the serialised PANDA state if a key
//    // exchange is ongoing.
//    byte[] pandaKeyExchange;
//
//    // pandaResult contains an error message in the event that a PANDA key
//    // exchange failed.
//    String pandaResult;

    public ArrayList<Event> events = new ArrayList<>();

    public Contact() {}

    public Contact(long id, String name) {
        this.id = id;
        this.name = name;
    }

    public String subline() {
        if(revokedUs)
            return "has revoked";
        if(isPending)
            return "pending";
//        if(pandaResult.length() > 0)
//            return "failed";
//        if(!isPending )//TODO rachet == null)
//            return "old ratchet";
        return "";
    }

    public String toString() {
        return name;
    }

    //TODO indicator func

    public void processKeyExchange(Pond.SignedKeyExchange signedKeyExchange) throws InvalidProtocolBufferException {
        //TODO check sig
        Pond.KeyExchange keyExchange = Pond.KeyExchange.parseFrom(signedKeyExchange.getSigned());
        theirServer = keyExchange.getServer();
        theirIdentityPublic = new PublicKey(keyExchange.getIdentityPublic().toByteArray());
        theirPub = new VerifyKey(keyExchange.getPublicKey().toByteArray());
        theirHMACPairs =  new ArrayList<>(keyExchange.getHmacPairsList());
        ratchet.completeKeyExchange(keyExchange);
    }

    public ArrayList<Pond.HMACPair> generateHMACPairs(int num) {
        ArrayList<Pond.HMACPair> pairs = new ArrayList<Pond.HMACPair>();
        for (int i = 0; i < num; i++) {
            try {
                Mac hmac = Mac.getInstance("HmacSHA256");
                hmac.init(Client.getInstance().hmacKey);
                SigningKey newSKey = new SigningKey();
                byte[] hmacBytes = hmac.doFinal(newSKey.getVerifyKey().toBytes());
                System.out.println("Lenght of HMAC: "+hmacBytes.length);

                myHMACs.add(new HMACPair(newSKey.getVerifyKey().toBytes(), hmacBytes));

                Pond.HMACPair.Builder hmacBuilder = Pond.HMACPair.newBuilder();
                hmacBuilder.setPulbicKey(ByteString.copyFrom(newSKey.getVerifyKey().toBytes()));
                hmacBuilder.setPrivateKey(ByteString.copyFrom(newSKey.toBytes()));
                hmacBuilder.setHmacOfPublicKey(ByteBuffer.wrap(hmacBytes).getLong());

                pairs.add(hmacBuilder.build());
                Client.getInstance().hmacIndex.put(ByteBuffer.wrap(hmacBytes).getLong(), id);
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            }
        }
        return pairs;
    }

    private HMACPair getMyPair(long hmac) {
        for(int i = 0; i < myHMACs.size(); i ++)
            if(myHMACs.get(i).getHmac() == hmac)
                return myHMACs.get(i);
        return null;
    }

    public boolean verifyMyPair(byte[] publicKey, long hmac) {
        HMACPair pair = getMyPair(hmac);
        if(pair != null &&
                MessageDigest.isEqual(publicKey, pair.publicKey) &&
                hmac == pair.getHmac())
            return true;
        return false;
    }
}
