package systems.obscure.client.client;

import com.google.protobuf.ByteString;

import org.abstractj.kalium.keys.PublicKey;
import org.abstractj.kalium.keys.VerifyKey;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import javax.crypto.Mac;

import systems.obscure.client.crypto.SigningKey;
import systems.obscure.client.protos.Pond;

/**
 * @author unixninja92
 */
public class Contact {
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



//    // generation is the current group generation number that we know for
//    // this contact.
//    Integer generation;

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

    public void processKeyExchange(Pond.KeyExchange keyExchange) {
        theirServer = keyExchange.getServer();
        theirIdentityPublic = new PublicKey(keyExchange.getIdentityPublic().toByteArray());
        theirPub = new VerifyKey(keyExchange.getPublicKey().toByteArray());
        theirHMACPairs =  new ArrayList<>(keyExchange.getHmacPairsList());
    }

    public ArrayList<Pond.HMACPair> generateHMACPairs(int num) {
        ArrayList<Pond.HMACPair> pairs = new ArrayList<Pond.HMACPair>();
        for (int i = 0; i < num; i++) {
            try {
                Mac hmac = Mac.getInstance("HmacSHA256");
                hmac.init(Client.getInstance().hmacKey);
                SigningKey newSKey = new SigningKey();
                byte[] hmacBytes = hmac.doFinal(newSKey.getVerifyKey().toBytes());

                myHMACs.add(new HMACPair(newSKey.getVerifyKey().toBytes(), hmacBytes));

                Pond.HMACPair.Builder hmacBuilder = Pond.HMACPair.newBuilder();
                hmacBuilder.setPulbicKey(ByteString.copyFrom(newSKey.getVerifyKey().toBytes()));
                hmacBuilder.setPrivateKey(ByteString.copyFrom(newSKey.toBytes()));
                hmacBuilder.setHmacOfPublicKey(ByteBuffer.wrap(hmacBytes).getLong());

                pairs.add(hmacBuilder.build());
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            }
        }
        return pairs;
    }
}
