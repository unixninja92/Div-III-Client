package systems.obscure.client.client;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.SecretKey;

/**
 * @author unixninja92
 */
public class HMACPair {
    byte[] publicKey;
    byte[] hmacOfKey;

    public HMACPair(byte[] publickey, SecretKey hmacKey) {
        this.publicKey = publickey;
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(hmacKey);
            hmacOfKey = hmac.doFinal(this.publicKey);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
    }

    public HMACPair(byte[] publickey,byte[] hmacOfKey) {
        this.publicKey = publickey;
        this.hmacOfKey = hmacOfKey;
    }

}
