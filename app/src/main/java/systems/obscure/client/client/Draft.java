package systems.obscure.client.client;

import java.util.HashMap;
import java.util.List;

import systems.obscure.client.protos.Pond;

/**
 * @author unixninja92
 */
public class Draft {
    Long id;
    Long created;
    Long to;
    String body;
    Long inReplyTo;
    List<Pond.Message.Attachment> attachments;
    List<Pond.Message.Detachment> detachments;

    // pendingDetachments is only used by the GTK UI.
    HashMap<Long, PendingDetachment> pendingDetachments;

    //TODO usageString func
}
