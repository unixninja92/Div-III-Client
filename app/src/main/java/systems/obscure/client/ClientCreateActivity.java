/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package systems.obscure.client;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBar;

import org.thoughtcrime.securesms.BaseActionBarActivity;

import info.guardianproject.onionkit.ui.OrbotHelper;
import systems.obscure.client.client.Client;


/**
 * Activity for creating a user's local encryption passphrase.
 *
 * @author Moxie Marlinspike
 */

public class ClientCreateActivity extends BaseActionBarActivity {

  public ClientCreateActivity() { }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.create_passphrase_activity);

    OrbotHelper oc = new OrbotHelper(this);
    if (!oc.isOrbotInstalled())
      oc.promptToInstall(this);
    else if (!oc.isOrbotRunning())
      oc.requestOrbotStart(this);

    initializeResources();
  }

  private void initializeResources() {
    getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
    getSupportActionBar().setCustomView(R.layout.light_centered_app_title);

    new SecretGenerator().execute();
  }

  private class SecretGenerator extends AsyncTask<String, Void, Void> {
    @Override
    protected Void doInBackground(String... params) {
      Client.getInstance();
      return null;
    }

    @Override
    protected void onPostExecute(Void param) {
      ClientCreateActivity.this.finish();
    }
  }
}
