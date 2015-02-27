package systems.obscure.servertesting;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;

import com.google.common.io.BaseEncoding;

import org.abstractj.kalium.keys.PublicKey;

import systems.obscure.servertesting.client.Client;
import systems.obscure.servertesting.client.Transport;


public class MainActivity extends ActionBarActivity {

    String address = "whirlpool.obscure.systems";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        byte[] pub = BaseEncoding.base32().decode("HU6S52V5AT444X3UA4GMDUFK2DAKBWPQDOLL6TARQQNKBX2RUMPQ");
        PublicKey serverKey = new PublicKey(pub);

        Client client = new Client();
        client.start();
        Transport transport = new Transport(client.identity, serverKey);

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
