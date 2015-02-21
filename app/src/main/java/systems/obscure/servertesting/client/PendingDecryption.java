package systems.obscure.servertesting.client;

/**
 * @author unixninja92
 */
// pendingDecryption represents a detachment decryption/download operation
// that's in progress. These are not saved to disk.
public class PendingDecryption {
    // index is used by the UI code and indexes the list of detachments in
    // a message.
    int index;

    // cancel is a func that causes the task to be canceled at some point
    // in the future.
    //TODO cancel func
}
