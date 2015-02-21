package systems.obscure.servertesting.client;

/**
 * @author unixninja92
 */
// pendingDetachment represents a detachment conversion/upload operation that's
// in progress. These are not saved to disk.
public class PendingDetachment {
    long size;
    String path;
    public void cancel() {
        //TODO write cancel fuc
    }
}
