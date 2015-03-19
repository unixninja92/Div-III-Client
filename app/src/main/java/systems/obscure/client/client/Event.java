package systems.obscure.client.client;

/**
 * @author unixninja92
 */
// Event represents a log entry. This does not apply to the global log, which
// is quite chatty, but rather to significant events related to a given
// contact. These events are surfaced in the UI and recorded in the statefile.
public class Event {
    Long time;
    String msg;
    public Event(Long time, String msg) {
        this.time = time;
        this.msg = msg;
    }
}
