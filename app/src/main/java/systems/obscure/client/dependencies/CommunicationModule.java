package systems.obscure.client.dependencies;

/**
 * @author unixninja92
 */

import android.content.Context;

import dagger.Module;
import systems.obscure.client.service.TransactService;

@Module(complete = false, injects = {TransactService.class})
public class CommunicationModule {
    private final Context context;

    public CommunicationModule(Context context) {
        this.context = context;
    }
}
