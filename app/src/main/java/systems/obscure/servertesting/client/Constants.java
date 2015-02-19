package systems.obscure.servertesting.client;

/**
 * Created by charles on 2/16/15.
 */
public class Constants {
    //The number of bytes of overhead when boxing a message with SecretBox.
    public static final int SECRETBOX_OVERHEAD = 16;

    // TransportSize is the number of bytes that all payloads are padded to before
    // sending on the network.
    public static final int TRANSPORT_SIZE = 16384 - 2 - SECRETBOX_OVERHEAD;

    // MessageOverhead is the number of bytes reserved for wrapping a Message up in
    // protobufs. That includes the overhead of the protobufs themselves, as well
    // as the metadata in the protobuf and the group signature.
    public static final int MESSAGE_OVERHEAD = 512;

    // MaxSerializedMessage is the maximum size of the serialized Message protobuf.
    // The message will end up looking like this:
    //    [length - 4 bytes       ]  | NaCl box  | Message that server sees.
    //    [nonce - 24 bytes               ]
    //
    //      [SECRETBOX_OVERHEAD - 16 bytes]
    //      [message count - 4 bytes      ]
    //      [prev message count - 4 bytes ]
    //      [ratchet public - 32 bytes    ]
    //      [inner nonce - 32 bytes       ]
    //
    //      [SECRETBOX_OVERHEAD - 16 bytes]
    //      [serialized message           ]
    public static final int MAX_SERIALIZED_MESSAGE = TRANSPORT_SIZE -
            (SECRETBOX_OVERHEAD + 4 + 4 + 32 + 24) - SECRETBOX_OVERHEAD - MESSAGE_OVERHEAD;
}
