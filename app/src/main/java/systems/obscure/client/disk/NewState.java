package systems.obscure.client.disk;

/**
 * @author unixninja92
 */
public class NewState {
    public byte[] state;
    public boolean RotateErasureStorage;
    public boolean Destruct;

    public NewState(byte[] s, boolean r, boolean d){
        state = s;
        RotateErasureStorage = r;
        Destruct = d;
    }
}
