package systems.obscure.client;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import org.thoughtcrime.securesms.ConversationListActivity;

import systems.obscure.client.client.ClientS;


public class MainActivity extends ActionBarActivity {

    Thread clientThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        System.out.println("We started!!");

//        new Thread(new Runnable() {
//            public void run() {
//                Client client = new Client();
//                client.start();
////                }
//            }
//        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();


        if(clientThread == null) {
            clientThread = new Thread(new Runnable() {
                public void run() {
//                    Client client = new Client(getFilesDir().getPath()+"/statefile",
//                            "RX4SBLINCG6TUCR7FJYMNNSA33QAPVJAEYA5ROT6QG4IPX7FXE7Q", "127.0.0.1:9050");
//                    client.start();
                    ClientS client = ClientS.getInstance();
                    client.start(getFilesDir().getPath()+"/statefile");
                }
            });
            clientThread.start();
        }
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

    public void launchContacts(View view) {
        Intent contacts = new Intent(MainActivity.this, ContactsListActivity.class);
        startActivity(contacts);
    }

    public void launchCamera(View view) {
        Intent camera = new Intent(MainActivity.this, CameraActivity.class);
        startActivity(camera);
    }

    public void launchPicture(View view) {
        Intent contacts = new Intent(MainActivity.this, PictureActivity.class);
        startActivity(contacts);
    }

    public void launchMessages(View view) {
        Intent messages = new Intent(MainActivity.this, ConversationListActivity.class);
        startActivity(messages);
    }
}
