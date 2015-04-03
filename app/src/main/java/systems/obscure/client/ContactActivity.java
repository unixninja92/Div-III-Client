package systems.obscure.client;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.view.Menu;
import android.view.MenuItem;

import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.crypto.MasterSecret;

import systems.obscure.client.client.Client;
import systems.obscure.client.client.Contact;


public class ContactActivity extends PassphraseRequiredActionBarActivity {

    Contact contact;
    Client client = Client.getInstance();
    MasterSecret masterSecret;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE);
        getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_ab_back);
        setContentView(R.layout.activity_contact);

        Intent intent = getIntent();
        masterSecret = intent.getParcelableExtra("master_secret");

        int contact_id = intent.getIntExtra("contact_id", 0);
        contact = client.contactList.get(contact_id);

        getSupportActionBar().setTitle(contact.toString());
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_contact, menu);
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
