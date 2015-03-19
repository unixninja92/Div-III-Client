package systems.obscure.client.client;

import systems.obscure.client.protos.Pond;

/**
 * @author unixninja92
 */
public class MessageSendResult {
    // If the id is zero then a message wasn't actually sent - this is just
    // the transact goroutine poking the UI because the queue has been
    // updated.
    Long id;

    // revocation optionally contains a revocation update that resulted
    // from attempting to send a message.
    Pond.SignedRevocation revocation;

    // extraRevocations optionally contains revocations further to
    // |revocation|. This is only non-empty if |revocation| is non-nil.
    Pond.SignedRevocation[] extraRevocations;
}
