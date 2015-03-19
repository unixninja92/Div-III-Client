package systems.obscure.client.client;

import systems.obscure.client.protos.Pond;

/**
 * @author unixninja92
 */
public class QueuedMessage {
    Pond.Request.Builder request;
    Long id;
    Long to;
    String server;
    Long created;
    Long sent;
    Long acked;
    boolean revocation;
    Pond.Message.Builder message;

    // sending is true if the transact goroutine is currently sending this
    // message. This is protected by the queueMutex. TODO adjust comment for java
    boolean sending;

    //TODO indicator func
}
