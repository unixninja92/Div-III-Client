package systems.obscure.client.ratchet;

/**
 * @author unixninja92
 */
public class SavedKey {
    byte[] key;
    long timestamp;

    public SavedKey(byte[] key, long timestamp) {
        this.key = key;
        this.timestamp = timestamp;
    }
}
