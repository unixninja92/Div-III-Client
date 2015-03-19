package systems.obscure.client.client;

import com.google.common.io.BaseEncoding;
import com.google.protobuf.ByteString;

import org.abstractj.kalium.keys.KeyPair;
import org.abstractj.kalium.keys.PublicKey;
import org.jcsp.lang.CSProcess;
import org.jcsp.lang.One2OneChannel;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.io.IOException;

import systems.obscure.client.Globals;
import systems.obscure.client.protos.Pond;

/**
 * @author unixninja92
 */
public class Network implements CSProcess {
    // nonceLen is the length of a NaCl nonce.
    int nonceLen = 24;
    // ephemeralBlockLen is the length of the signcrypted, ephemeral key
    // used when Contact.supportedVersion >= 1.
    int ephemeralBlockLen = nonceLen + 32 + Globals.SECRETBOX_OVERHEAD;

//    Transport transport;

    Client client = Client.getInstance();

    public void sendAck(InboxMessage msg) {
        // First, see if we can merge this ack with a message to the same
        // contact that is pending transmission.
        client.queueLock.readLock().lock();
        for(QueuedMessage queuedMsg: client.queue){
            if(queuedMsg.sending)
                continue;
            if(msg.from == queuedMsg.to && !queuedMsg.revocation) {
                client.queueLock.readLock().unlock();
                client.queueLock.writeLock().lock();
                Pond.Message.Builder messBuilder = queuedMsg.message;
                messBuilder.getAlsoAckList().add(msg.message.getId());
                //TODO write tooLarge()
//                if(!tooLarge(queuedMsg)){
//                    queuedMsg.message = messBuilder.build();
//                    client.queueLock.writeLock().unlock();
//                    System.out.println("ACK merged with queued message.");
//                    //All done
//                    return;
//                }
                messBuilder.getAlsoAckList().remove(messBuilder.getAlsoAckCount()-1);
                if(messBuilder.getAlsoAckCount() == 0)
                    messBuilder.clearAlsoAck();
                queuedMsg.message = messBuilder;
                client.queueLock.writeLock().unlock();
            }
        }
        client.queueLock.readLock().unlock();

        Contact to = client.contacts.get(msg.from);

        //TODO var myNextDH []byte

        long id = client.randId();

        Pond.Message.Builder message = Pond.Message.newBuilder();
        message.setId(id);
//        message.setTime()
        message.setBody(ByteString.copyFrom(new byte[1]));
        message.setBodyEncoding(Pond.Message.Encoding.RAW);
//        message.setMyNextDh()
        message.setInReplyTo(msg.message.getId());
        message.setSupportedVersion(Globals.PROTO_VERSION);
        send(to, message);
    }

    public void send(Contact to, Pond.Message.Builder messageBuilder) {
//        Pond.Message message = messageBuilder.build();

        if(tooLarge(messageBuilder))
            throw new IllegalStateException("message too large");

        QueuedMessage out = new QueuedMessage();
        out.id = messageBuilder.getId();
        out.to = to.id;
        out.server = to.theirServer;
        out.message = messageBuilder;
//        out.created =
        client.enqueue(out);
//        client.outbox.add(out); TODO make outbox an ArrayList
    }

    private boolean tooLarge(Pond.Message.Builder msg) {
        Pond.Message message = msg.build();

        return message.getSerializedSize() > Globals.MAX_SERIALIZED_MESSAGE;
    }

    public Transport dialServer(String server, boolean useRandomIdentity) {
        KeyPair identity;
        if(useRandomIdentity)
            identity = new KeyPair();
        else
            identity = client.identity;

        byte[] pub = BaseEncoding.base32().decode(server);
        PublicKey serverKey = new PublicKey(pub);

        Transport transport = new Transport(identity, serverKey);
        try {
            transport.handshake();
            return transport;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void doCreateAccount() {
        client.generation = client.randId().intValue();

        Pond.NewAccount.Builder newAccount = Pond.NewAccount.newBuilder();
        newAccount.setGeneration(client.generation);
//        newAccount.setGroup(ByteString.copyFrom(client.hmacKey));
        newAccount.setHmacKey(ByteString.copyFrom(client.hmacKey));

        Pond.Request.Builder request = Pond.Request.newBuilder();
        request.setNewAccount(newAccount);

        try {
            Transport transport = dialServer(client.server, false);
            transport.writeProto(request);
            Pond.Reply reply = transport.readProto();
            replyToError(reply);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        TextSecurePreferences.setRegisteredOnServer(client.context, true);

    }

    public void replyToError(Pond.Reply reply) throws IOException {
        if(reply.hasStatus() || reply.getStatus() == Pond.Reply.Status.OK)
            return;
        if(reply.getStatus().getNumber()<29 && reply.getStatus().getNumber()>=0)
            throw new IOException("error from server: "+reply.getStatus());
        else
            throw new IOException("unknown error from server: "+reply.getStatus());
    }

    //TODO uploadDetachment

    //TODO downloadDetachment

    //TODO transferDetachmentConn

    //TODO transferDetachment

    @Override
    public void run() {
        boolean startup = true;
        One2OneChannel<Boolean> ackChan;
        QueuedMessage head = null;
        boolean lastWasSend = false;

        while (true) {
            if(head != null) {
                // We failed to send a message.
                client.queueLock.writeLock().lock();
                head.sending = false;
                client.queueLock.writeLock().unlock();
                head = null;
            }
        }
    }


}
