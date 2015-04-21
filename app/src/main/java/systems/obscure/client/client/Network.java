package systems.obscure.client.client;

import com.google.common.io.BaseEncoding;
import com.google.protobuf.ByteString;

import org.abstractj.kalium.keys.KeyPair;
import org.abstractj.kalium.keys.PublicKey;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import systems.obscure.client.Globals;
import systems.obscure.client.protos.Pond;

/**
 * @author unixninja92
 */
public class Network {
    // nonceLen is the length of a NaCl nonce.
    final int nonceLen = 24;
    // ephemeralBlockLen is the length of the signcrypted, ephemeral key
    // used when Contact.supportedVersion >= 1.
    final int ephemeralBlockLen = nonceLen + 32 + Globals.SECRETBOX_OVERHEAD;

    public static Client client = Client.getInstance();

    public static void sendAck(InboxMessage msg) {
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
                if(!tooLarge(queuedMsg.message)){
                    queuedMsg.message = messBuilder;
                    client.queueLock.writeLock().unlock();
                    System.out.println("ACK merged with queued message.");
                    //All done
                    return;
                }
                messBuilder.getAlsoAckList().remove(messBuilder.getAlsoAckCount()-1);
                if(messBuilder.getAlsoAckCount() == 0)
                    messBuilder.clearAlsoAck();
                queuedMsg.message = messBuilder;
                client.queueLock.writeLock().unlock();
            }
        }
        client.queueLock.readLock().unlock();

        Contact to = client.contacts.get(msg.from);


        long id = client.randId();

        Pond.Message.Builder message = Pond.Message.newBuilder();
        message.setId(id);
        message.setTime(System.nanoTime());
        message.setBody(ByteString.copyFrom(new byte[1]));
        message.setBodyEncoding(Pond.Message.Encoding.RAW);

//        message.setMyNextDh()TODO set this
        message.setInReplyTo(msg.message.getId());
        message.setSupportedVersion(Globals.PROTO_VERSION);
        send(to, message);
    }

    public static void send(Contact to, Pond.Message.Builder messageBuilder) {

        if(tooLarge(messageBuilder))
            throw new IllegalStateException("message too large");

        QueuedMessage out = new QueuedMessage();
        out.id = messageBuilder.getId();
        out.to = to.id;
        out.server = to.theirServer;
        out.message = messageBuilder;
        out.created = messageBuilder.getTime();
        client.enqueue(out);
        client.outbox.put(out.id, out);
    }

    public static void processSigningRequest(SigningRequest signingRequest){
        Contact to = client.contacts.get(signingRequest.msg.to);

        byte[] message = signingRequest.msg.message.build().toByteArray();

        if(message.length > Globals.MAX_SERIALIZED_MESSAGE)
            throw new RuntimeException("Failed to sign outgoing message because it's too large");

        ByteBuffer plaintext = ByteBuffer.allocate(Globals.MAX_SERIALIZED_MESSAGE+4);
        plaintext.putInt(message.length);
        plaintext.put(message);
        byte[] randBytes = new byte[plaintext.remaining()];
        client.rand.nextBytes(randBytes);
        plaintext.put(randBytes);

        byte[] sealed = to.ratchet.encrypt(plaintext.array());

        try {
            MessageDigest sha = MessageDigest.getInstance("SHA256");
            byte[] digest = sha.digest(sealed);
            //TODO sign digest

            Pond.Delivery.Builder deliver = Pond.Delivery.newBuilder();
            deliver.setTo(ByteString.copyFrom(to.theirIdentityPublic.toBytes()));
            //TODO HMAC stuff
            deliver.setMessage(ByteString.copyFrom(sealed));

            Pond.Request.Builder request = Pond.Request.newBuilder();
            request.setDeliver(deliver);

            signingRequest.resultChan.write(request);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    private static boolean tooLarge(Pond.Message.Builder msg) {
        Pond.Message message = msg.build();

        return message.getSerializedSize() > Globals.MAX_SERIALIZED_MESSAGE;
    }

    public static Transport dialServer(String server, boolean useRandomIdentity) throws IOException {
        KeyPair identity;
        if(useRandomIdentity)
            identity = new KeyPair();
        else
            identity = client.identity;

        byte[] pub = BaseEncoding.base32().decode(server);
        PublicKey serverKey = new PublicKey(pub);

        Transport transport = new Transport(identity, serverKey);
        transport.handshake();
        return transport;
    }

    public static void doCreateAccount() {

        Pond.NewAccount.Builder newAccount = Pond.NewAccount.newBuilder();
//        newAccount.setGeneration(client.generation);
//        newAccount.setGroup(ByteString.copyFrom(client.hmacKey));
        newAccount.setHmacKey(ByteString.copyFrom(client.hmacKey.getEncoded()));

        Pond.Request.Builder request = Pond.Request.newBuilder();
        request.setNewAccount(newAccount);

        try {
            Transport transport = dialServer(client.server, false);
            transport.writeProto(request);
            Pond.Reply reply = transport.readProto();
            replyToError(reply);
            transport.Close();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        TextSecurePreferences.setRegisteredOnServer(Globals.applicaiontContext, true);
        System.out.println("Account create finished");

    }

    public static void replyToError(Pond.Reply reply) throws IOException {
        if(reply.hasStatus() || reply.getStatus() == Pond.Reply.Status.OK)
            return;
        if(reply.getStatus().getNumber()<29 && reply.getStatus().getNumber()>=0)
            throw new IOException("error from server: "+reply.getStatus());
        else
            throw new IOException("unknown error from server: "+reply.getStatus());
    }

}
