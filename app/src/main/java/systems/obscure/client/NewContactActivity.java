package systems.obscure.client;

import android.os.Bundle;
import android.support.v7.app.ActionBar;

import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;


public class NewContactActivity extends PassphraseRequiredActionBarActivity {

    private final DynamicTheme dynamicTheme    = new DynamicTheme();
    private final DynamicLanguage dynamicLanguage = new DynamicLanguage();
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

//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        // Inflate the menu; this adds items to the action bar if it is present.
//        getMenuInflater().inflate(R.menu.menu_new_contact, menu);
//        return true;
//    }
//
//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        // Handle action bar item clicks here. The action bar will
//        // automatically handle clicks on the Home/Up button, so long
//        // as you specify a parent activity in AndroidManifest.xml.
//        int id = item.getItemId();
//
//        //noinspection SimplifiableIfStatement
//        if (id == R.id.action_settings) {
//            return true;
//        }
//
//        return super.onOptionsItemSelected(item);
//    }
}
