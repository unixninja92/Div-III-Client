package systems.obscure.client.util;

import org.jcsp.lang.ChannelOutput;

import java.util.TimerTask;

/**
 * @author unixninja92
 */
public class TimerChanTask extends TimerTask {
    private ChannelOutput<Long> chan;
    public TimerChanTask(ChannelOutput<Long> c){
        super();
        chan = c;
    }
    @Override
    public void run() {
        chan.write(System.nanoTime());
    }
}
