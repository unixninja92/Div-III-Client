package systems.obscure.client.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.apache.commons.math3.random.MersenneTwister;
import org.jcsp.lang.Alternative;
import org.jcsp.lang.Any2OneChannel;
import org.jcsp.lang.Channel;
import org.jcsp.lang.Guard;
import org.jcsp.lang.One2OneChannel;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.jobqueue.requirements.RequirementListener;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Timer;

import info.guardianproject.onionkit.ui.OrbotHelper;
import systems.obscure.client.Globals;
import systems.obscure.client.client.Client;
import systems.obscure.client.client.MessageSendResult;
import systems.obscure.client.client.Network;
import systems.obscure.client.client.NewMessage;
import systems.obscure.client.client.QueuedMessage;
import systems.obscure.client.protos.Pond;
import systems.obscure.client.util.TimerChanTask;

/**
 * @author unixninja92
 */
public class TransactService extends Service implements Runnable, InjectableType, RequirementListener {

    public static final  String ACTION_ACTIVITY_STARTED  = "ACTIVITY_STARTED";
    public static final  String ACTION_ACTIVITY_FINISHED = "ACTIVITY_FINISHED";

    private NetworkRequirement networkRequirement;
//    private NetworkRequirementProvider networkRequirementProvider;

    private Client client;

    private OrbotHelper orbotHelper;


    private int     activeActivities = 0;


    private boolean startup;
    private Any2OneChannel<Boolean> ackChan = null;
    private QueuedMessage head = null;
    private boolean lastWasSend;

    @Override
    public void onCreate() {
        super.onCreate();
        client = Client.getInstance(getApplicationContext());

//        ApplicationContext.getInstance(this).injectDependencies(this);

        System.out.println("Is this made?");
        networkRequirement         = new NetworkRequirement(this);
//        networkRequirementProvider = new NetworkRequirementProvider(this);
//
//        networkRequirementProvider.setListener(this);

        orbotHelper = new OrbotHelper(this);
        startup = true;
        lastWasSend = false;
        new Thread(this, "TransactService").start();
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;

        if      (ACTION_ACTIVITY_STARTED.equals(intent.getAction()))  incrementActive();
        else if (ACTION_ACTIVITY_FINISHED.equals(intent.getAction())) decrementActive();

        return START_STICKY;
    }
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onRequirementStatusChanged() {
        synchronized (this) {
            notifyAll();
        }
    }

    @Override
    public void run() {
        System.out.println("It's runing!");

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
                    long delay = ((long)(delaySeconds * 1000)) * 1000;//TODO verify this math is correct.
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

            transact();
        }
    }

    private synchronized void transact() {
        waitForNetwork();
        System.out.println("Done waiting!!");
        Pond.Request.Builder req;
        String server;
        boolean useAnonymousIdentity = true;
        boolean isFetch = false;

        client.queueLock.readLock().lock();
        System.out.println("got that queue lock!!");
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

        Pond.Reply reply = Network.sendRecv(server, useAnonymousIdentity, lastWasSend, head, req);

        if(reply == null){
            if(!isFetch){
                client.queueLock.writeLock().lock();
                client.moveContactsMessagesToEndOfQueue(head.to);
                client.queueLock.writeLock().unlock();
            }
            return;
        }

        if(!isFetch){
            client.queueLock.writeLock().lock();

            if(!client.queue.contains(head))
                return;

            head.sending = false;

            if(!reply.hasStatus()) {
                client.removeQueuedMessage(head);
                client.queueLock.writeLock().unlock();
                client.messageSentChan.out().write(new MessageSendResult(head.id));
            } else {
                client.moveContactsMessagesToEndOfQueue(head.to);
                client.queueLock.writeLock().unlock();

                if(reply.getStatus() == Pond.Reply.Status.GENERATION_REVOKED && reply.hasRevocation()){
                    client.messageSentChan.out().write(new MessageSendResult(head.id, reply.getRevocation(), reply.getExtraRevocationsList()));
                }
            }
            head = null;
        } else if(reply.hasFetched() || reply.hasAnnounce()) {
            ackChan = Channel.any2one();
            client.newMessageChan.out().write(new NewMessage(reply.getFetched(), reply.getAnnounce(), ackChan.out()));
            ackChan.in().read();
        }

        try {
            Network.replyToError(reply);
        } catch (IOException e) {
            System.out.print("Error from server " + server + ": ");
            e.printStackTrace();
            return;
        }
    }

    private synchronized boolean canTransact() {
        return orbotHelper.isOrbotRunning() //&& networkRequirement.isPresent()
                && activeActivities > 0;
    }

    private synchronized void waitForNetwork() {
        try {
            while (!canTransact()) wait();
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    private synchronized void incrementActive() {
        activeActivities++;
//        Log.w(TAG, "Active Count: " + activeActivities);
        notifyAll();
    }

    private synchronized void decrementActive() {
        activeActivities--;
//        Log.w(TAG, "Active Count: " + activeActivities);
        notifyAll();
    }


    public static void registerActivityStarted(Context activity) {
        Intent intent = new Intent(activity, TransactService.class);
        intent.setAction(TransactService.ACTION_ACTIVITY_STARTED);
        activity.startService(intent);
    }

    public static void registerActivityStopped(Context activity) {
        Intent intent = new Intent(activity, TransactService.class);
        intent.setAction(TransactService.ACTION_ACTIVITY_FINISHED);
        activity.startService(intent);
    }
}
