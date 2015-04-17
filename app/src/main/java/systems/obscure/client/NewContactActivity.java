package systems.obscure.client;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.view.View;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.Dialogs;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;

import java.io.IOException;

import systems.obscure.client.client.Contact;
import systems.obscure.client.protos.Pond;


public class NewContactActivity extends PassphraseRequiredActionBarActivity {

    private final DynamicTheme dynamicTheme    = new DynamicTheme();
    private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

    private Contact contact;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        dynamicTheme.onCreate(this);
        dynamicLanguage.onCreate(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_contact);

        getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE);
        getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_ab_back);

        getSupportActionBar().setTitle("New Contact");
    }

    @Override
    public void onResume() {
        super.onResume();
        dynamicTheme.onResume(this);
        dynamicLanguage.onResume(this);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);

        if ((scanResult != null) && (scanResult.getContents() != null)) {
            String data = scanResult.getContents();

            try {
                Pond.KeyExchange receivedKeyExchange = Pond.KeyExchange.parseFrom(Base64.decode(data));
                contact.processKeyExchange(receivedKeyExchange);
                Dialogs.showInfoDialog(this, "Contact Scanned", "Contacts key exchange successfully scanned.");
            } catch (IOException e) {
                Dialogs.showAlertDialog(this, "Contact Scan Failed", "An error occurred while scanning your contact.");
                e.printStackTrace();
            }
        } else {
            Toast.makeText(this, R.string.KeyScanningActivity_no_scanned_key_found_exclamation,
                    Toast.LENGTH_LONG).show();
        }
    }

    private IntentIntegrator getIntentIntegrator() {
        IntentIntegrator intentIntegrator = new IntentIntegrator(this);
        intentIntegrator.setButtonYesByID(R.string.yes);
        intentIntegrator.setButtonNoByID(R.string.no);
        intentIntegrator.setTitleByID(R.string.KeyScanningActivity_install_barcode_Scanner);
        intentIntegrator.setMessageByID(R.string.KeyScanningActivity_this_application_requires_barcode_scanner_would_you_like_to_install_it);
        return intentIntegrator;
    }

    public void initiateScan(View view) {
        IntentIntegrator intentIntegrator = getIntentIntegrator();
        intentIntegrator.initiateScan();
    }

    public void initiateDisplay(View view) {
        IntentIntegrator intentIntegrator = getIntentIntegrator();
        intentIntegrator.shareText(Base64.encodeBytes(contact.kxsBytes));
    }

    public void createContact(View view) {
//        contact.name =
    }
}
