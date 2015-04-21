package systems.obscure.client.client;

import com.google.common.io.BaseEncoding;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.abstractj.kalium.keys.KeyPair;
import org.abstractj.kalium.keys.PublicKey;
import org.abstractj.kalium.keys.SigningKey;
import org.abstractj.kalium.keys.VerifyKey;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

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
            Pond.HMACPair pair = to.theirHMACPairs.get(0);
            to.theirHMACPairs.remove(0);
            //TODO revoke HMAC pair
            SigningKey signingKey = new SigningKey(pair.getPrivateKey().toByteArray());
            byte[] sig = signingKey.sign(digest);

            Pond.Delivery.Builder deliver = Pond.Delivery.newBuilder();
            deliver.setTo(ByteString.copyFrom(to.theirIdentityPublic.toBytes()));
            deliver.setMessage(ByteString.copyFrom(sealed));
            deliver.setHmacOfPublicKey(pair.getHmacOfPublicKey());
            deliver.setOneTimePublicKey(ByteString.copyFrom(signingKey.toBytes()));
            deliver.setOneTimeSignature(ByteString.copyFrom(sig));

            Pond.Request.Builder request = Pond.Request.newBuilder();
            request.setDeliver(deliver);

            signingRequest.resultChan.write(request);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    private byte[] decryptMessage(byte[] sealed, Contact from) {
        return from.ratchet.decrypt(sealed);
    }

//    public byte[] decryptMessageInner(byte[] sealed, byte[] nonce, Contact from) {
//        SecretBox secretBox;
//        try {
//            secretBox = new SecretBox()
//        }
//    }

    public void processNewMessage(NewMessage m) {
        if(m.fetched != null)
            processFetch(m);
//        else
//            processServerAnnounce(m);
        m.ack.write(true);
    }

    private void processFetch(NewMessage m) {
        Pond.Fetched f = m.fetched;


        Long id = client.hmacIndex.get(f.getHmacOfPublicKey());
        Contact from = client.contacts.get(id);
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA256");
            byte[] digest = sha.digest(f.getMessage().toByteArray());

            if(id != null) {
                if(!from.verifyMyPair(f.getOneTimePublicKey().toByteArray(),
                        f.getHmacOfPublicKey())) {
                    System.out.println("Provieded key does not match stored key");
                    return;
                }
                VerifyKey key = new VerifyKey(f.getOneTimePublicKey().toByteArray());
                if(!key.verify(f.getMessage().toByteArray(), f.getOneTimeSignature().toByteArray())){
                    System.out.println("Received message with bad signature!");
                    return;
                }
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return;
        }

        if(from.revoked) {
            System.out.println("Message from revoked contact "+from+". Dropping");
            return;
        }

        InboxMessage inboxMsg = new InboxMessage();
        inboxMsg.id = client.randId();
        inboxMsg.receivedTime = System.nanoTime();
        inboxMsg.from = from.id;
        inboxMsg.sealed = f.getMessage().toByteArray();
        if(!from.isPending)
            if(!unsealMessage(inboxMsg, from) || inboxMsg.message.getBody().size() == 0)
                return;

        client.inbox.put(inboxMsg.id, inboxMsg);
        client.save();
        //TODO notify UI
    }

    public boolean unsealMessage(InboxMessage message, Contact from) {
        if(from.isPending)
            throw new RuntimeException("was asked to unseal message from pending contact");

        byte[] sealed = message.sealed;
        ByteBuffer plaintext = ByteBuffer.wrap(decryptMessage(sealed, from));

        if(plaintext.capacity() < 4) {
            System.out.println("Plaintext too small to process");
            return false;
        }

        int mLen = plaintext.getInt();
        if(mLen < 0 || mLen > plaintext.remaining()) {
            System.out.println("Plaintext length incorrect: "+mLen);
            return false;
        }
        plaintext.limit(mLen + plaintext.position());

        byte[] msgBytes = new byte[mLen];
        plaintext.get(msgBytes);
        Pond.Message msg;
        try {
             msg = Pond.Message.parseFrom(msgBytes);
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
            return false;
        }
        for(InboxMessage candidate: client.inbox.values()) {
            if(candidate.from == from.id &&
                    candidate.id != message.id &&
                    candidate.message != null &&
                    candidate.message.getId() == msg.getId()) {
                System.out.println("Dropping duplicate message from "+from.name);
                return false;
            }
        }

        ArrayList<Long> ackedIds = new ArrayList<>();
        for(Long ack: msg.getAlsoAckList())
            ackedIds.add(ack);
        if(msg.hasInReplyTo())
            ackedIds.add(msg.getInReplyTo());

        long now = System.nanoTime();

        for(Long ack: ackedIds) {
            for(QueuedMessage candidate: client.outbox.values()) {
                if(candidate.id == ack) {
                    candidate.acked = now;
                    //TODO process ack
                    break;
                }
            }
        }

        if(msg.hasSupportedVersion())
            from.supportedVersion = msg.getSupportedVersion();

        from.kxsBytes = null;
        message.message = msg;
        message.sealed = null;
        message.read = false;

        return true;
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
