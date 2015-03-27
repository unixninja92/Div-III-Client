package systems.obscure.client.jobs;

import android.content.Context;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.jobs.MasterSecretJob;
import org.whispersystems.jobqueue.JobParameters;

/**
 * @author unixninja92
 */
public class TransactJob extends MasterSecretJob {
    public TransactJob(Context context, JobParameters parameters){
        super(context, parameters);
    }
    @Override
    public void onRun(MasterSecret masterSecret) throws Exception {

    }

    @Override
    public boolean onShouldRetryThrowable(Exception exception) {
        return false;
    }

    @Override
    public void onAdded() {

    }

    @Override
    public void onCanceled() {

    }
}
