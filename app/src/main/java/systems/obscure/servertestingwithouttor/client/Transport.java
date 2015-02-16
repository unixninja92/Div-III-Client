package systems.obscure.servertestingwithouttor.client;

import android.os.StrictMode;

import org.abstractj.kalium.keys.KeyPair;
import org.abstractj.kalium.keys.PublicKey;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Created by charles on 2/16/15.
 */
public class Transport {
    private Socket serverSocket;
    private BufferedInputStream read;
    private BufferedOutputStream write;
    private KeyPair identity;
    private PublicKey serverIdentity;


    String address = "whirlpool.obscure.systems";

    public Transport(KeyPair i, PublicKey server) {
        identity = i;
        serverIdentity = server;

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        serverSocket = new Socket();
        try {
            InetSocketAddress serverAddress = new InetSocketAddress(Inet4Address.getByName(address), 16333);
            serverSocket.connect(serverAddress);
            read = new BufferedInputStream(serverSocket.getInputStream());
            write = new BufferedOutputStream(serverSocket.getOutputStream());


        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
