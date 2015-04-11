package systems.obscure.client.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import org.jcsp.lang.AltingChannelInput;
import org.jcsp.lang.ChannelOutput;
import org.thoughtcrime.securesms.jobs.requirements.MasterSecretRequirement;
import org.thoughtcrime.securesms.jobs.requirements.MasterSecretRequirementProvider;
import org.whispersystems.jobqueue.requirements.RequirementListener;

import systems.obscure.client.Globals;
import systems.obscure.client.client.Client;
import systems.obscure.client.disk.NewState;
import systems.obscure.client.disk.StateFile;

/**
 * @author unixninja92
 */
public class StateService extends Service implements Runnable, RequirementListener {
    public static final  String ACTION_ACTIVITY_STARTED  = "ACTIVITY_STARTED";
    public static final  String ACTION_ACTIVITY_FINISHED = "ACTIVITY_FINISHED";

    private Client client;

    private MasterSecretRequirement masterSecretRequirement;
    private MasterSecretRequirementProvider masterSecretRequirementProvider;

    private int     activeActivities = 0;

    AltingChannelInput<NewState> in;
    ChannelOutput out;

    StateFile stateFile;
    @Override
    public void onCreate() {
        super.onCreate();

        masterSecretRequirement = new MasterSecretRequirement(this);
        masterSecretRequirementProvider = new MasterSecretRequirementProvider(this);

        masterSecretRequirementProvider.setListener(this);

//        Any2OneChannel chan = Channel.any2one();
//        in = Globals.stateIn;
//        out = Globals.stateDone;

        new Thread(this, "StateService").start();
    }

//    @Override
//    public int onStartCommand(Intent intent, int flags, int startId) {
//        if (intent == null) return START_NOT_STICKY;
//
//        intent.getExtra
//
//        return START_NOT_STICKY;
//    }

    @Override
    public void run() {
        waitForMasterSecret();

        client = Client.getInstance();

        in = Globals.stateIn;
        out = Globals.stateDone;
        stateFile = client.stateFile;

        stateFile.StartWrtie(in, out);
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

    private synchronized void waitForMasterSecret() {
        try {
            while (!masterSecretRequirement.isPresent()) wait();
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }
}
