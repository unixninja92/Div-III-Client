package systems.obscure.servertesting.client;

import systems.obscure.servertesting.protos.SignedRevocation;

/**
 * @author unixninja92
 */
public class MessageSendResult {
    // If the id is zero then a message wasn't actually sent - this is just
    // the transact goroutine poking the UI because the queue has been
    // updated.
    int id;

    // revocation optionally contains a revocation update that resulted
    // from attempting to send a message.
    SignedRevocation revocation;

    // extraRevocations optionally contains revocations further to
    // |revocation|. This is only non-empty if |revocation| is non-nil.
    SignedRevocation[] extraRevocations;
}
