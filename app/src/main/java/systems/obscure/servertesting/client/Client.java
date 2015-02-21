package systems.obscure.servertesting.client;

import org.abstractj.kalium.keys.KeyPair;
import org.abstractj.kalium.keys.SigningKey;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * @author unixninja92
 */
public class Client {
    // messageLifetime is the default amount of time for which we'll keep a
    // message. (Counting from the time that it was received.)
    public final int messageLifetime = 7 * 24;


    // The current protocol version implemented by this code.
    int protoVersion = 1;

    // stateFilename is the filename of the file on disk in which we
    // load/save our state.
    String stateFile;

    // server is the URL of the user's home server.
    String server;

    // identity is a curve25519 private value that's used to authenticate
    // the client to its home server.
    KeyPair identity;

    // groupPriv is the group private key for the user's delivery group.
    byte[] groupPriv;

    // prevGroupPrivs contains previous group private keys that have been
    // revoked. This allows us to process messages that were inflight at
    // the time of the revocation.
    byte[][] preGroupPriv;

    // generation is the generation number of the group private key and is
    // incremented when a member of the group is revoked.
    Integer generation;

    // siging Ed25519 keypair.
    SigningKey signingKey;

    public void start() {
        boolean newAccount = true;

        if(newAccount) {
            try {
                SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
                MessageDigest digest = MessageDigest.getInstance("SHA256");
                byte[] seed = new byte[32];
                random.nextBytes(seed);
                signingKey = new SigningKey(digest.digest(seed));
                identity = new KeyPair();


            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
