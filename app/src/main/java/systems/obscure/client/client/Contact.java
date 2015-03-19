package systems.obscure.client.client;

import org.abstractj.kalium.keys.PublicKey;
import org.abstractj.kalium.keys.VerifyKey;

import java.util.ArrayList;

/**
 * @author unixninja92
 */
public class Contact {
    // id is only locally valid.
    long id;

    // name is the friendly name that the user chose for this contact. It
    // is unique for all contacts.
    String name;

    // isPending is true if we haven't received a key exchange message from
    // this contact.
    boolean isPending = true;

    // kxsBytes is the serialised key exchange message that we generated
    // for this contact. (Only valid if |isPending| is true.)
    byte[] kxsBytes;

    // groupKey is the group member key that we gave to this contact.
    // groupKey TODO bbsig.MemberKey
    // myGroupKey is the one that they gave to us.
    // myGroupKey TODO bbsig.MemberKey

    // generation is the current group generation number that we know for
    // this contact.
    Integer generation;

    // theirServer is the URL of the contact's home server.
    String theirServer;

    // theirPub is their Ed25519 public key.
    VerifyKey theirPub;

    // theirIdentityPublic is the public identity that their home server
    // knows them by.
    PublicKey theirIdentityPublic;

    // supportedVersion contains the greatest protocol version number that
    // we have observed from this contact.
    int supportedVersion;

    // revoked is true if this contact has been revoked.
    boolean revoked = false;

    // revokedUs is true if this contact has revoked us.
    boolean revokedUs = false;

    // pandaKeyExchange contains the serialised PANDA state if a key
    // exchange is ongoing.
    byte[] pandaKeyExchange;

    // pandaResult contains an error message in the event that a PANDA key
    // exchange failed.
    String pandaResult;

    ArrayList<Event> events;

    public String subline() {
        if(revokedUs)
            return "has revoked";
        if(isPending)
            return "pending";
        if(pandaResult.length() > 0)
            return "failed";
        if(!isPending )//TODO rachet == null)
            return "old ratchet";
        return "";
    }

    //TODO indicator func

    //TODO processKeyExchange func
}
