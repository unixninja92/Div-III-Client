package systems.obscure.servertesting.client;

import systems.obscure.servertesting.protos.Fetch;
import systems.obscure.servertesting.protos.ServerAnnounce;

/**
 * @author unixninja92
 */
// NewMessage is sent from the network goroutine to the client goroutine and
// contains messages fetched from the home server. TODO fix comment for java
public class NewMessage {
    Fetch fetched;
    ServerAnnounce serverAnnounce;
    boolean ack = false;
}
