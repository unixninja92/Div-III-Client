package systems.obscure.servertesting.client;

import systems.obscure.servertesting.protos.Pond;

/**
 * @author unixninja92
 */
// NewMessage is sent from the network goroutine to the client goroutine and
// contains messages fetched from the home server. TODO fix comment for java
public class NewMessage {
    Pond.Fetch fetched;
    Pond.ServerAnnounce serverAnnounce;
    boolean ack = false; //TODO make chan
}
