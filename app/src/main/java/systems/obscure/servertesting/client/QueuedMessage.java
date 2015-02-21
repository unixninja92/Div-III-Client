package systems.obscure.servertesting.client;

import systems.obscure.servertesting.protos.ProtoMessage;
import systems.obscure.servertesting.protos.Request;

/**
 * @author unixninja92
 */
public class QueuedMessage {
    Request request;
    Long id;
    Long to;
    String server;
    int created;
    int sent;
    int acked;
    boolean revocation;
    ProtoMessage message;

    // sending is true if the transact goroutine is currently sending this
    // message. This is protected by the queueMutex. TODO adjust comment for java
    boolean sending;

    //TODO indicator func
}
