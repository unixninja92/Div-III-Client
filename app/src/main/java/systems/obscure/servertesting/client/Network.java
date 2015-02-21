package systems.obscure.servertesting.client;

/**
 * @author unixninja92
 */
public class Network {
    // nonceLen is the length of a NaCl nonce.
    int nonceLen = 24;
    // ephemeralBlockLen is the length of the signcrypted, ephemeral key
    // used when Contact.supportedVersion >= 1.
    int ephemeralBlockLen = nonceLen + 32 + Constants.SECRETBOX_OVERHEAD;


}
