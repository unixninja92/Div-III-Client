package systems.obscure.servertesting.client;

import com.google.common.primitives.UnsignedLong;

import systems.obscure.servertesting.protos.Pond;

/**
 * @author unixninja92
 */
public class QueuedMessage {
    Pond.Request request;
    UnsignedLong id;
    UnsignedLong to;
    String server;
    int created;
    int sent;
    int acked;
    boolean revocation;
    Pond.Message message;

    // sending is true if the transact goroutine is currently sending this
    // message. This is protected by the queueMutex. TODO adjust comment for java
    boolean sending;

    //TODO indicator func
}
