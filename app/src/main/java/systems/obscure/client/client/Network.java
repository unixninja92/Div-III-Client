package systems.obscure.client.client;

import com.google.common.io.BaseEncoding;
import com.google.protobuf.ByteString;

import org.abstractj.kalium.keys.KeyPair;
import org.abstractj.kalium.keys.PublicKey;
import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.apache.commons.math3.random.MersenneTwister;
import org.jcsp.lang.Alternative;
import org.jcsp.lang.Any2OneChannel;
import org.jcsp.lang.CSProcess;
import org.jcsp.lang.Channel;
import org.jcsp.lang.Guard;
import org.jcsp.lang.One2OneChannel;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Timer;

import systems.obscure.client.Globals;
import systems.obscure.client.protos.Pond;
import systems.obscure.client.util.TimerChanTask;

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
        message.setTime(System.nanoTime());
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
        out.created = messageBuilder.getTime();
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
        Any2OneChannel<Boolean> ackChan = null;
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

            if(!startup || !client.autoFetch) {
                if(ackChan != null){
                    ackChan.out().write(true);
                    ackChan = null;
                }

                One2OneChannel<Long> timerChan = Channel.one2one(5);
                if(client.autoFetch) {
                    byte[] seedBytes = new byte[8];
                    client.rand.nextBytes(seedBytes);
                    long seed = ByteBuffer.wrap(seedBytes).getLong();
                    ExponentialDistribution distribution = new ExponentialDistribution(new MersenneTwister(seed), 1);
                    double delaySeconds = distribution.sample() * Globals.TRANSACTION_RATE_SECONDS;
                    long delay = ((long)(delaySeconds * 1000)) * 1000;
                    System.out.println("Next network transaction in "+delay+" milliseconds");
                    Timer timer = new Timer();
                    timer.schedule(new TimerChanTask(timerChan.out()), delay);
                }

                Guard[] chans = {client.fetchNowChan.in(), timerChan.in()};
                Alternative alt = new Alternative(chans);
                switch (alt.select()){
                    case 0: ackChan = client.fetchNowChan;
                        System.out.println("Starting fetch because of fetchNow signal");
                        break;
                    case 1: System.out.println("Starting fetch because of timer");
                        break;
                }
            }
            startup = false;

            Pond.Request.Builder req;
            String server;
            boolean useAnonymousIdentity = true;
            boolean isFetch = false;

            client.queueLock.readLock().lock();
            if(lastWasSend || client.queue.size() == 0){
                useAnonymousIdentity = false;
                isFetch = true;
                req = Pond.Request.newBuilder();
                req.setFetch(Pond.Fetch.newBuilder());
                server = client.server;
                System.out.println("Starting fetch from home server");
                lastWasSend = false;
            } else {
                head = client.queue.peek();
                client.queueLock.readLock().unlock();
                client.queueLock.writeLock().lock();
                head.sending = true;
                client.queueLock.writeLock().unlock();
                client.queueLock.readLock().lock();
                req = head.request;
                server = head.server;
                System.out.println("Starting message transmission to "+server);

                if(head.revocation)
                    useAnonymousIdentity = false;
                lastWasSend = true;
            }
            client.queueLock.readLock().unlock();

            // Poke the UI thread so that it knows that a message has
            // started sending.
            client.messageSentChan.out().write(new MessageSendResult());
        }
    }


}
