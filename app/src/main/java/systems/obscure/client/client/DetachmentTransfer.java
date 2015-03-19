package systems.obscure.client.client;

import java.io.IOException;

import systems.obscure.client.protos.Pond;

/**
 * @author unixninja92
 */
// detachmentTransfer is the interface to either an upload or download so that
// the code for moving the bytes can be shared between them.
public interface DetachmentTransfer {
    // Request returns the request that should be sent to the server.
    public Pond.Request Request();

    // ProcessReply returns a file to read/write from, the starting offset
    // for the transfer and the total size of the file. The file will
    // already have been positioned correctly.
    public ProcessedDetachmentReply ProcessReply(Pond.Reply reply) throws IOException;

    // Complete is called once the bytes have been transfered. It trues true on success.
    public boolean Complete(Transport trans);
}
