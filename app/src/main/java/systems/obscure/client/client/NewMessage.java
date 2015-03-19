package systems.obscure.client.client;

import org.jcsp.lang.SharedChannelOutput;

import systems.obscure.client.protos.Pond;

/**
 * @author unixninja92
 */
// NewMessage is sent from the network routine to the client routine and
// contains messages fetched from the home server.
public class NewMessage {
    Pond.Fetched fetched;
    Pond.ServerAnnounce serverAnnounce;
    SharedChannelOutput<Boolean> ack;

    public NewMessage(Pond.Fetched f, Pond.ServerAnnounce announce, SharedChannelOutput<Boolean> a) {
        fetched = f;
        serverAnnounce = announce;
        ack = a;
    }
}
