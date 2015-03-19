package systems.obscure.client.client;

import android.net.Uri;

/**
 * @author unixninja92
 */
public class KnowServer {
    String nickname;
    String description;
    Uri uri;

    public KnowServer(String nickname, String description, Uri uri) {
        this.nickname = nickname;
        this.description = description;
        this.uri = uri;
    }
}
