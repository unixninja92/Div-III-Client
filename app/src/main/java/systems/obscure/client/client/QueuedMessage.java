package systems.obscure.client.client;

import systems.obscure.client.protos.Pond;

/**
 * @author unixninja92
 */
public class QueuedMessage {
    public Pond.Request.Builder request;
    public Long id;
    public Long to;
    public String server;
    public Long created;
    Long sent;
    Long acked;
    public boolean revocation;
    public Pond.Message.Builder message;

    // sending is true if the transact goroutine is currently sending this
    // message. This is protected by the queueMutex. TODO adjust comment for java
    public boolean sending;

    //TODO indicator func
}
