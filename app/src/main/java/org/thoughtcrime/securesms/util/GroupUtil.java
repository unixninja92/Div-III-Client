package org.thoughtcrime.securesms.util;

import org.thoughtcrime.securesms.util.Hex;

import java.io.IOException;

//import static org.whispersystems.textsecure.internal.push.PushMessageProtos.PushMessageContent.GroupContext;

public class GroupUtil {

  private static final String ENCODED_GROUP_PREFIX = "__textsecure_group__!";

  public static String getEncodedId(byte[] groupId) {
    return ENCODED_GROUP_PREFIX + org.thoughtcrime.securesms.util.Hex.toStringCondensed(groupId);
  }

  public static byte[] getDecodedId(String groupId) throws IOException {
    if (!isEncodedGroup(groupId)) {
      throw new IOException("Invalid encoding");
    }

    return Hex.fromStringCondensed(groupId.split("!", 2)[1]);
  }

  public static boolean isEncodedGroup(String groupId) {
    return groupId.startsWith(ENCODED_GROUP_PREFIX);
  }

//  public static String getDescription(Context context, String encodedGroup) {
//    if (encodedGroup == null) {
//      return context.getString(R.string.GroupUtil_group_updated);
//    }
//
//    try {
//      StringBuilder description  = new StringBuilder();
//      GroupContext  groupContext = GroupContext.parseFrom(Base64.decode(encodedGroup));
//      List<String>  members      = groupContext.getMembersList();
//      String        title        = groupContext.getName();
//
//      if (!members.isEmpty()) {
//        description.append(context.getString(R.string.GroupUtil_joined_the_group, Util.join(members, ", ")));
//      }
//
//      if (title != null && !title.trim().isEmpty()) {
//        if (description.length() > 0) description.append(" ");
//        description.append(context.getString(R.string.GroupUtil_title_is_now, title));
//      }
//
//      if (description.length() > 0) {
//        return description.toString();
//      } else {
//        return context.getString(R.string.GroupUtil_group_updated);
//      }
//    } catch (InvalidProtocolBufferException e) {
//      Log.w("GroupUtil", e);
//      return context.getString(R.string.GroupUtil_group_updated);
//    } catch (IOException e) {
//      Log.w("GroupUtil", e);
//      return context.getString(R.string.GroupUtil_group_updated);
//    }
//  }
}
