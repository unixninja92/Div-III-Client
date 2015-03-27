package systems.obscure.client.client;

import org.jcsp.lang.ChannelOutput;

import systems.obscure.client.protos.Pond;

/**
 * @author unixninja92
 */
// signingRequest is a structure that is sent from the network thread to the
// main thread to request that a message be signed with a group signature for
// delivery.
public class SigningRequest {
    public QueuedMessage msg;
    public ChannelOutput<Pond.Request.Builder> resultChan;
}
