package systems.obscure.servertesting.client;

import systems.obscure.servertesting.protos.Request;

/**
 * @author unixninja92
 */
// signingRequest is a structure that is sent from the network thread to the
// main thread to request that a message be signed with a group signature for
// delivery.
public class SigningRequest {
    QueuedMessage msg;
    Request request; //TODO make chan
}
