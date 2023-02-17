package com.github.warren_bank.bonjour_webrtc.ui;

import com.github.warren_bank.bonjour_webrtc.R;
import com.github.warren_bank.bonjour_webrtc.data_model.ServerListItem;
import com.github.warren_bank.bonjour_webrtc.data_model.SharedPrefs;
import com.github.warren_bank.bonjour_webrtc.lock_management.MulticastLockMgr;
import com.github.warren_bank.bonjour_webrtc.security_model.RuntimePermissions;
import com.github.warren_bank.bonjour_webrtc.service.ServerService;
import com.github.warren_bank.bonjour_webrtc.util.OrgAppspotApprtcGlue;
import com.github.warren_bank.bonjour_webrtc.util.Util;

import org.appspot.apprtc.SettingsActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.Inet4Address;
import java.util.ArrayList;

import javax.jmdns.impl.util.ByteWrangler;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    private ListView                      listView;
    private ArrayList<ServerListItem>     listItems;
    private ArrayAdapter<ServerListItem>  listAdapter;

    private ServerListItem selectedServerListItem;

    private String BONJOUR_SERVICE_TYPE;
    private BonjourServiceListener bonjourServiceListener;
    private JmDNS bonjour;

    private class BonjourServiceListener implements ServiceListener {
        @Override
        public void serviceAdded(ServiceEvent event) {
            Log.d(TAG, "Service added: " + event.getName());

            // request that service be resolved
            bonjour.requestServiceInfo(event.getType(), event.getName(), 1);
        }

        @Override
        public void serviceResolved(ServiceEvent event) {
            Log.d(TAG, "Service resolved: " + event.getName());

            ServerListItem item = getServerListItem(event);
            if (item == null)
                return;

            int position = listItems.indexOf(item);
            if (position >= 0)
                return;

            String local_IP = ServerService.getLocalIp();
            if ((local_IP != null) && (local_IP.equals(item.ip)))
                return;

            listItems.add(item);
            updateUiThread();
        }

        @Override
        public void serviceRemoved(ServiceEvent event) {
            Log.d(TAG, "Service removed: " + event.getName());

            String         ip   = event.getName();
            ServerListItem item = new ServerListItem(null, ip);

            int position = listItems.indexOf(item);
            if (position == -1)
                return;

            while (position != -1) {
                listItems.remove(position);
                position = listItems.indexOf(item);
            }

            updateUiThread();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Bonjour events helper:
    // ---------------------------------------------------------------------------------------------

    private ServerListItem getServerListItem(ServiceEvent event) {
        ServiceInfo info = event.getInfo();
        if (info == null)
            return null;

        String title = ByteWrangler.readUTF(info.getTextBytes());
        String ip    = event.getName();

        if ((title == null) || (ip == null))
            return null;

        title = title.trim();
        ip    = ip.trim();

        if (title.isEmpty() || ip.isEmpty())
            return null;

        ServerListItem item = new ServerListItem(title, ip);
        return item;
    }

    private void updateUiThread() {
        MainActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                listAdapter.notifyDataSetChanged();
            }
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Lifecycle Events:
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView    = (ListView) findViewById(R.id.listview);
        listItems   = new ArrayList<ServerListItem>();
        listAdapter = new ArrayAdapter<ServerListItem>(MainActivity.this, android.R.layout.simple_list_item_1, listItems);
        listView.setAdapter(listAdapter);

        selectedServerListItem = null;
        BONJOUR_SERVICE_TYPE   = getString(R.string.constant_bonjour_service_type);
        bonjourServiceListener = new BonjourServiceListener();

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectedServerListItem = listItems.get(position);
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                String ip = listItems.get(position).ip;
                Toast.makeText(MainActivity.this, ip, Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        SharedPrefs.setDefaultPreferenceValues(MainActivity.this);
        OrgAppspotApprtcGlue.setDefaultPreferenceValues(MainActivity.this);

        ServerService.doStart(MainActivity.this);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.e("MainActivity", "onDestroy");

//        ServerService.doStop(MainActivity.this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        new Thread(){
            public void run() {
                try {
                    if (!ServerService.isStarted())
                        MulticastLockMgr.acquire(MainActivity.this);

//                    bonjour = JmDNS.create(Util.getWlanIpAddress_InetAddress(MainActivity.this));

//                    bonjour.addServiceListener(BONJOUR_SERVICE_TYPE, bonjourServiceListener);
                }
                catch(Exception e) {}
            }
        }.start();
    }

    @Override
    protected void onPause() {
        super.onPause();

        try {
            if (!ServerService.isStarted())
                MulticastLockMgr.release();

            if (bonjour != null) {
                bonjour.removeServiceListener(BONJOUR_SERVICE_TYPE, bonjourServiceListener);
                bonjour = null;
            }
        }
        catch(Exception e) {}
    }

    // ---------------------------------------------------------------------------------------------
    // ActionBar:
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getActionBar().setDisplayShowHomeEnabled(false);
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch(menuItem.getItemId()) {
            case R.id.menu_toggle_server: {
                selectedServerListItem = null;

                return true;
            }
            case R.id.menu_update_server_alias: {
                openUpdateServerAliasDialog();
                return true;
            }
            case R.id.menu_open_settings: {
                openSettingsDialog();
                return true;
            }
            default: {
                return super.onOptionsItemSelected(menuItem);
            }
        }
    }


    // ---------------------------------------------------------------------------------------------
    // enable/disable local server and its Bonjour registration on LAN:
    // ---------------------------------------------------------------------------------------------

    private void toggleServer() {
        ServerService.doStart(MainActivity.this);
    }

    // ---------------------------------------------------------------------------------------------
    // connect to remote server on LAN:
    // ---------------------------------------------------------------------------------------------

    private void connectToServer(ServerListItem serverListItem) {
        String serverIpAddress = serverListItem.ip;

        Intent intent = OrgAppspotApprtcGlue.getCallActivityIntent(MainActivity.this, serverIpAddress);
        startActivity(intent);
    }

    // ---------------------------------------------------------------------------------------------
    // open dialog: Update Server Alias
    // ---------------------------------------------------------------------------------------------

    private void openUpdateServerAliasDialog() {
        String oldServerAlias = SharedPrefs.getServerAlias(MainActivity.this);

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle(R.string.dialog_update_server_alias_title);

        final EditText input_server_alias = new EditText(MainActivity.this);
        input_server_alias.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        input_server_alias.setText(oldServerAlias, TextView.BufferType.EDITABLE);
        builder.setView(input_server_alias);

        builder.setPositiveButton(R.string.dialog_update_server_alias_positive, new DialogInterface.OnClickListener() { 
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String newServerAlias = input_server_alias.getText().toString().trim();
                if (!newServerAlias.equals(oldServerAlias)) {
                    SharedPrefs.putServerAlias(MainActivity.this, newServerAlias);
                }
                dialog.dismiss();
            }
        });

        builder.setNegativeButton(R.string.dialog_update_server_alias_negative, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    // ---------------------------------------------------------------------------------------------
    // open dialog: Settings
    // ---------------------------------------------------------------------------------------------

    private void openSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle(R.string.dialog_open_settings_menu_title);
        builder.setItems(R.array.dialog_open_settings_menu_options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent;

                switch (which + 1) {
                    case 1:
                        // General
                        dialog.dismiss();
                        intent = new Intent(MainActivity.this, GeneralSettingsActivity.class);
                        startActivity(intent);
                        break;
                    case 2:
                        // WebRTC
                        dialog.dismiss();
                        intent = new Intent(MainActivity.this, SettingsActivity.class);
                        startActivity(intent);
                        break;
                    default:
                        dialog.cancel();
                        break;
                }
            }
        });
        builder.show();
    }
}
