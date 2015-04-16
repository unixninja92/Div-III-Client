package systems.obscure.client.client;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.SecretKey;

/**
 * @author unixninja92
 */
public class HMACPair {
    byte[] key;
    byte[] hmacOfKey;

    public HMACPair(byte[] key, SecretKey hmacKey) {
        this.key = key;
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(hmacKey);
            hmacOfKey = hmac.doFinal(key);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
    }

}
