package systems.obscure.servertesting.client;

import com.google.common.primitives.UnsignedLong;

import java.util.HashMap;
import java.util.List;

import systems.obscure.servertesting.protos.Pond;

/**
 * @author unixninja92
 */
public class Draft {
    UnsignedLong id;
    //time created; TODO figure out time
    UnsignedLong to;
    String body;
    UnsignedLong inReplyTo;
    List<Pond.Message.Attachment> attachments;
    List<Pond.Message.Detachment> detachments;

    // pendingDetachments is only used by the GTK UI.
    HashMap<Long, PendingDetachment> pendingDetachments;

    //TODO usageString func
}
