package systems.obscure.client.client;

import java.util.List;

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
//    Pond.SignedRevocation revocation;
    Pond.HMACStrike revocation;

    // extraRevocations optionally contains revocations further to
    // |revocation|. This is only non-empty if |revocation| is non-nil.
    List<Pond.HMACStrike> extraRevocations;

    public MessageSendResult() {}

    public MessageSendResult(long id) {this.id = id;}

    public MessageSendResult(long id, Pond.HMACStrike revocation) {
        this(id);
        this.revocation = revocation;
    }

    public MessageSendResult(long id, Pond.HMACStrike revocation, List<Pond.HMACStrike> extraRevocations) {
        this(id, revocation);
        this.extraRevocations = extraRevocations;
    }
}
