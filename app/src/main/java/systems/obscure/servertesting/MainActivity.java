package systems.obscure.servertesting;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;

import systems.obscure.servertesting.client.Client;


public class MainActivity extends ActionBarActivity {

//    String address = "zkpp.obscure.systems";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        new Thread(new Runnable() {
            public void run() {
//                byte[] pub = BaseEncoding.base32().decode("RX4SBLINCG6TUCR7FJYMNNSA33QAPVJAEYA5ROT6QG4IPX7FXE7Q");
//                PublicKey serverKey = new PublicKey(pub);
                Client client = new Client();
                client.start();
//                Transport transport = new Transport(client.identity, serverKey);
//                try {
//                    transport.handshake();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
            }
        }).start();

//        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
//        StrictMode.setThreadPolicy(policy);
//
//        Socket serverSocket = new Socket();
//        try {
//            InetSocketAddress serverAddress = new InetSocketAddress(Inet4Address.getByName(address), 16333);
//            serverSocket.connect(serverAddress);
//            InputStream in = serverSocket.getInputStream();
//            OutputStream out = serverSocket.getOutputStream();
//            System.out.println("Is connected:"+serverSocket.isConnected());
//
//            Client client = new Client();
//            client.start();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
