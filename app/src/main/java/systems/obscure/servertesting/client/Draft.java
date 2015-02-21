package systems.obscure.servertesting.client;

import java.util.HashMap;
import java.util.List;

import systems.obscure.servertesting.protos.ProtoMessage;

/**
 * @author unixninja92
 */
public class Draft {
    long id;
    //time created; TODO figure out time
    long to;
    String body;
    long inReplyTo;
    List<ProtoMessage.Attachment> attachments;
    List<ProtoMessage.Detachment> detachments;

    // pendingDetachments is only used by the GTK UI.
    HashMap<Long, PendingDetachment> pendingDetachments;

    //TODO usageString func
}
