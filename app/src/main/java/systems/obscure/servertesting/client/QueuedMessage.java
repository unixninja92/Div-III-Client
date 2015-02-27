package systems.obscure.servertesting.client;

import com.google.common.primitives.UnsignedLong;

import systems.obscure.servertesting.protos.ProtoMessage;
import systems.obscure.servertesting.protos.Request;

/**
 * @author unixninja92
 */
public class QueuedMessage {
    Request request;
    UnsignedLong id;
    UnsignedLong to;
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
