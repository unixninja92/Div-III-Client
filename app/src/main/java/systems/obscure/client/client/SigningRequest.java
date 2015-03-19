package systems.obscure.client.client;

import org.jcsp.lang.SharedChannelInput;

import systems.obscure.client.protos.Pond;

/**
 * @author unixninja92
 */
// signingRequest is a structure that is sent from the network thread to the
// main thread to request that a message be signed with a group signature for
// delivery.
public class SigningRequest {
    QueuedMessage msg;
    SharedChannelInput<Pond.Request> resultChan;
}
