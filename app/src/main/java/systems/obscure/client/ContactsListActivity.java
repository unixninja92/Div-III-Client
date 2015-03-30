package systems.obscure.client;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.android.AndroidUtilities;
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;


public class ContactsListActivity extends PassphraseRequiredActionBarActivity {
    private ImageView backButtonImageView;

    ImageView addContactButton;
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contacts_list);

//        Toolbar toolbar = (Toolbar) findViewById(R.id.contacts_list_toolbar);
//        toolbar.setTitle(R.string.contacts);
//        toolbar.hideOverflowMenu();
//        toolbar.setLogo(R.drawable.ic_ab_back);
////        toolbar.set
////        toolbar.setBackButtonImage(R.drawable.ic_ab_back);
//        setSupportActionBar(toolbar);

        addContactButton = (ImageView) findViewById(R.id.);
        addContactButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                System.out.println("pressed");
                //show new contact dialog
            }
        });
    }


//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        // Inflate the menu; this adds items to the action bar if it is present.
////        getMenuInflater().inflate(R.menu.menu_contacts, menu);
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

//    private void createBackButtonImage() {
//        if (backButtonImageView != null) {
//            return;
//        }
//        backButtonImageView = new ImageView(getContext());
//        titleFrameLayout.addView(backButtonImageView);
//        backButtonImageView.setScaleType(ImageView.ScaleType.CENTER);
//        backButtonImageView.setBackgroundResource(itemsBackgroundResourceId);
//        backButtonImageView.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if (isSearchFieldVisible) {
//                    closeSearchField();
//                    return;
//                }
//                if (actionBarMenuOnItemClick != null) {
//                    actionBarMenuOnItemClick.onItemClick(-1);
//                }
//            }
//        });
//    }

    private void positionBackImage(int height) {
        if (backButtonImageView != null) {
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)backButtonImageView.getLayoutParams();
            layoutParams.width = AndroidUtilities.dp(54);
            layoutParams.height = height;
            layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
            backButtonImageView.setLayoutParams(layoutParams);
        }
    }
}
