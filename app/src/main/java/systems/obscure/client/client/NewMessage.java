package systems.obscure.client.client;

import org.jcsp.lang.AltingChannelInput;

import systems.obscure.client.protos.Pond;

/**
 * @author unixninja92
 */
// NewMessage is sent from the network routine to the client routine and
// contains messages fetched from the home server.
public class NewMessage {
    Pond.Fetch fetched;
    Pond.ServerAnnounce serverAnnounce;
    AltingChannelInput<Boolean> ack;

    public NewMessage(Pond.Fetch f, Pond.ServerAnnounce announce, AltingChannelInput<Boolean> a) {
        fetched = f;
        serverAnnounce = announce;
        ack = a;
    }
}
