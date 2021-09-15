package com.a_sboev.sip;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;



import org.pjsip.pjsua2.AccountConfig;
import org.pjsip.pjsua2.AuthCredInfo;
import org.pjsip.pjsua2.AuthCredInfoVector;
import org.pjsip.pjsua2.BuddyConfig;
import org.pjsip.pjsua2.CallInfo;
import org.pjsip.pjsua2.CallOpParam;
import org.pjsip.pjsua2.CallSetting;
import org.pjsip.pjsua2.StringVector;
import org.pjsip.pjsua2.pjsip_inv_state;
import org.pjsip.pjsua2.pjsip_status_code;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements Handler.Callback, MyAppObserver
{
    private String callNumber="";
    private String digitStr="";
    public static MyCall currentCall = null;
    public static MyApp app = null;
    public static MyAccount account = null;
    private final Handler handler = new Handler(this);
    private int buddyListSelectedIdx = -1;
    ArrayList<Map<String, String>> buddyList;
    private SimpleAdapter buddyListAdapter;
    private String lastRegStatus = "";
    public static AccountConfig accCfg = null;
    private ListView buddyListView;





    public void notifyRegState(int code, String reason, long expiration)
    {
        String msg_str = "";
        if (expiration == 0)
            msg_str += "Unregistration";
        else
            msg_str += "Registration";

        if (code/100 == 2)
            msg_str += " successful";
        else
            msg_str += " failed: " + reason;

        Message m = Message.obtain(handler, MSG_TYPE.REG_STATE, msg_str);
        m.sendToTarget();
    }


    public void notifyIncomingCall(MyCall call)
    {
        Message m = Message.obtain(handler, MSG_TYPE.INCOMING_CALL, call);
        m.sendToTarget();
    }

    @Override
    public void notifyCallState(MyCall call)
    {
        if (currentCall == null || call.getId() != currentCall.getId())
            return;

        CallInfo ci;
        try {
            ci = call.getInfo();
        } catch (Exception e) {
            ci = null;
        }
        Message m = Message.obtain(handler, MSG_TYPE.CALL_STATE, ci);
        m.sendToTarget();
    }

    @Override
    public void notifyBuddyState(MyBuddy buddy)
    {
        Message m = Message.obtain(handler, MSG_TYPE.BUDDY_STATE, buddy);
        m.sendToTarget();
    }


    public class MSG_TYPE
    {
        public final static int INCOMING_CALL = 1;
        public final static int CALL_STATE = 2;
        public final static int REG_STATE = 3;
        public final static int BUDDY_STATE = 4;
    }

    private HashMap<String, String> putData(String uri, String status)
    {
        HashMap<String, String> item = new HashMap<String, String>();
        item.put("uri", uri);
        item.put("status", status);
        return item;
    }

    private void showCallActivity()
    {
        Intent intent = new Intent(this, CallActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final EditText callNumberEditText = (EditText) findViewById(R.id.CallNumbEditText);
        final Handler handler = new Handler();
        checkPermissions();


        handler.post(new Runnable()
        {
            @Override
            public void run()
            {
                callNumber = callNumberEditText.getText().toString();
                //TextView textView = (TextView) findViewById(R.id.textView);
                //textView.setText(callNumber);
                handler.postDelayed(this, 100);
            }
        });

        if (app == null)
        {
            app = new MyApp();
            /* Wait for GDB to init */
            if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0)
            {
                try
                {
                    Thread.sleep(5000);
                }
                catch (InterruptedException e)
                {}
            }
            app.init(this, getFilesDir().getAbsolutePath());
        }
        if (app.accList.size() == 0)
        {
            accCfg = new AccountConfig();
            accCfg.setIdUri("sip:localhost");
            accCfg.getNatConfig().setIceEnabled(true);
            account = app.addAcc(accCfg);
        }
        else
        {
            account = app.accList.get(0);
            accCfg = account.cfg;
        }

        buddyList = new ArrayList<Map<String, String>>();
        for (int i = 0; i < account.buddyList.size(); i++)
        {
            buddyList.add(putData(account.buddyList.get(i).cfg.getUri(), account.buddyList.get(i).getStatusText()));
        }
        String[] from = { "uri", "status" };
        int[] to = { R.id.text_1, R.id.text_2 };

        buddyListAdapter = new SimpleAdapter(this, buddyList, R.layout.simple_list_item, from, to);
        buddyListView = (ListView) findViewById(R.id.listViewBuddy);;
        buddyListView.setAdapter(buddyListAdapter);
        buddyListView.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> parent, final View view,
                                    int position, long id)
            {
                view.setSelected(true);
                buddyListSelectedIdx = position;
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.action_acc_config:
                dlgAccountSetting();
                break;

            case R.id.action_quit:
                Message m = Message.obtain(handler, 0);
                m.sendToTarget();
                break;

            default:
                break;
        }

        return true;
    }

    private void dlgAccountSetting()
    {

        LayoutInflater li = LayoutInflater.from(this);
        View view = li.inflate(R.layout.dlg_account_config, null);

        if (!lastRegStatus.isEmpty()) {
            TextView tvInfo = (TextView)view.findViewById(R.id.textView2);
            tvInfo.setText("Last status: " + lastRegStatus);
        }

        AlertDialog.Builder adb = new AlertDialog.Builder(this);
        adb.setView(view);
        adb.setTitle("Account Settings");

        final EditText etId    = (EditText)view.findViewById(R.id.editTextId);
        final EditText etReg   = (EditText)view.findViewById(R.id.editTextRegistrar);
        final EditText etProxy = (EditText)view.findViewById(R.id.editTextProxy);
        final EditText etUser  = (EditText)view.findViewById(R.id.editTextUsername);
        final EditText etPass  = (EditText)view.findViewById(R.id.editTextPassword);

        etId.setText(accCfg.getIdUri());
        etReg.setText(modifyUfi(accCfg.getRegConfig().getRegistrarUri()));
        StringVector proxies = accCfg.getSipConfig().getProxies();
        if (proxies.size() > 0)
            etProxy.setText(proxies.get(0));
        else
            etProxy.setText("");
        AuthCredInfoVector creds = accCfg.getSipConfig().getAuthCreds();
        if (creds.size() > 0)
        {
            etUser. setText(creds.get(0).getUsername());
            etPass. setText(creds.get(0).getData());
        }
        else
        {
            etUser. setText("");
            etPass. setText("");
        }

        adb.setCancelable(false);
        adb.setPositiveButton("OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,int id) {
                        String acc_id 	 = "sip:4002@10.99.0.21"; //+ etId.getText().toString() + "@" + etReg.getText().toString();
                        String registrar = "sip:10.99.0.21"; //+ etReg.getText().toString();//etReg.getText().toString();
                        String proxy 	 = "";//etProxy.getText().toString();
                        String username  = "4002";//etUser.getText().toString();
                        String password  = etPass.getText().toString();

                        accCfg.setIdUri(acc_id);
                        accCfg.getRegConfig().setRegistrarUri(registrar);
                        AuthCredInfoVector creds = accCfg.getSipConfig().getAuthCreds();
                        creds.clear();
                        if (!username.isEmpty()) {
                            creds.add(new AuthCredInfo("Digest", "*", username, 0, password));
                        }
                        StringVector proxies = accCfg.getSipConfig().getProxies();
                        proxies.clear();
                        if (!proxy.isEmpty())
                        {
                            proxies.add(proxy);
                        }

                        /* Enable ICE */
                        //accCfg.getNatConfig().setIceEnabled(true);

                        /* Finally */
                        lastRegStatus = "";
                        try {
                            account.modify(accCfg);
                        } catch (Exception e)
                        {System.out.println(e);}
                    }
                });
        adb.setNegativeButton("Cancel",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,int id) {
                        dialog.cancel();
                    }
                });

        AlertDialog ad = adb.create();
        ad.show();
    }

    public void makeCall(View view)
    {
        /*if (buddyListSelectedIdx == -1)
            return;*/

        /* Only one call at anytime */
        if (currentCall != null)
        {
            return;
        }
        String registrationUri = accCfg.getRegConfig().getRegistrarUri();
        //HashMap<String, String> item = (HashMap<String, String>) buddyListView.getItemAtPosition(buddyListSelectedIdx);
        String buddy_uri = "sip:"+callNumber+"@" + modifyUfi(registrationUri);//item.get("uri");

        MyCall call = new MyCall(account, -1);
        CallOpParam prm = new CallOpParam();
        CallSetting opt = prm.getOpt();
        opt.setAudioCount(1);
        opt.setVideoCount(0);

        try
        {
            call.makeCall(buddy_uri, prm);
        }
        catch (Exception e)
        {
            currentCall = null;
            System.out.println(e);
            return;
        }

        currentCall = call;
        EditText callNumberEditText = (EditText) findViewById(R.id.CallNumbEditText);
        callNumber="";
        callNumberEditText.setText(callNumber);
        showCallActivity();
    }

    private void dlgAddEditBuddy(BuddyConfig initial)
    {
        final BuddyConfig cfg = new BuddyConfig();
        final BuddyConfig old_cfg = initial;
        final boolean is_add = initial == null;

        LayoutInflater li = LayoutInflater.from(this);
        View view = li.inflate(R.layout.dlg_add_buddy, null);

        AlertDialog.Builder adb = new AlertDialog.Builder(this);
        adb.setView(view);

        final EditText etUri    = (EditText)view.findViewById(R.id.CallNumbEditText);
        final CheckBox cbSubs  = (CheckBox)view.findViewById(R.id.checkBoxSubscribe);

        if (is_add)
        {
            adb.setTitle("Add Buddy");
        }
        else {
            adb.setTitle("Edit Buddy");
            etUri. setText(initial.getUri());
            cbSubs.setChecked(initial.getSubscribe());
        }

        adb.setCancelable(false);
        adb.setPositiveButton("OK",
                new DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog,int id)
                    {
                        cfg.setUri(etUri.getText().toString());
                        cfg.setSubscribe(cbSubs.isChecked());

                        if (is_add)
                        {
                            account.addBuddy(cfg);
                            buddyList.add(putData(cfg.getUri(), ""));
                            buddyListAdapter.notifyDataSetChanged();
                            buddyListSelectedIdx = -1;
                        }
                        else
                        {
                            if (!old_cfg.getUri().equals(cfg.getUri())) {
                                account.delBuddy(buddyListSelectedIdx);
                                account.addBuddy(cfg);
                                buddyList.remove(buddyListSelectedIdx);
                                buddyList.add(putData(cfg.getUri(), ""));
                                buddyListAdapter.notifyDataSetChanged();
                                buddyListSelectedIdx = -1;
                            }
                            else if (old_cfg.getSubscribe() != cfg.getSubscribe())
                            {
                                MyBuddy bud = account.buddyList.get(buddyListSelectedIdx);
                                try
                                {
                                    bud.subscribePresence(cfg.getSubscribe());
                                }
                                catch (Exception e)
                                {}
                            }
                        }
                    }
                });
        adb.setNegativeButton("Cancel",
                new DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog,int id) {
                        dialog.cancel();
                    }
                });

        AlertDialog ad = adb.create();
        ad.show();
    }

    public void addBuddy(View view)
    {
        dlgAddEditBuddy(null);
    }

    public void editBuddy(View view)
    {
        if (buddyListSelectedIdx == -1)
            return;

        BuddyConfig old_cfg = account.buddyList.get(buddyListSelectedIdx).cfg;
        dlgAddEditBuddy(old_cfg);
    }

    public void delBuddy(View view) {
        if (buddyListSelectedIdx == -1)
            return;

        final HashMap<String, String> item = (HashMap<String, String>) buddyListView.getItemAtPosition(buddyListSelectedIdx);
        String buddy_uri = item.get("uri");

        DialogInterface.OnClickListener ocl = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        account.delBuddy(buddyListSelectedIdx);
                        buddyList.remove(item);
                        buddyListAdapter.notifyDataSetChanged();
                        buddyListSelectedIdx = -1;
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        break;
                }
            }
        };

        AlertDialog.Builder adb = new AlertDialog.Builder(this);
        adb.setTitle(buddy_uri);
        adb.setMessage("\nDelete this buddy?\n");
        adb.setPositiveButton("Yes", ocl);
        adb.setNegativeButton("No", ocl);
        adb.show();
    }



    @Override
    public void onStart()
    {
        super.onStart();
        Start();
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
    }

    @Override
    public boolean handleMessage(Message m)
    {

        if (m.what == 0)
        {

            app.deinit();
            finish();
            Runtime.getRuntime().gc();
            android.os.Process.killProcess(android.os.Process.myPid());

        }
        else if (m.what == MSG_TYPE.CALL_STATE)
        {

            CallInfo ci = (CallInfo) m.obj;

            /* Forward the message to CallActivity */
            if (CallActivity.handler_ != null)
            {
                Message m2 = Message.obtain(CallActivity.handler_, MSG_TYPE.CALL_STATE, ci);
                m2.sendToTarget();
            }

            if (ci.getState() == pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED)
                currentCall = null;

        }
        else if (m.what == MSG_TYPE.BUDDY_STATE)
        {

            MyBuddy buddy = (MyBuddy) m.obj;
            int idx = account.buddyList.indexOf(buddy);

            /* Update buddy status text, if buddy is valid and
             * the buddy lists in account and UI are sync-ed.
             */
            if (idx >= 0 && account.buddyList.size() == buddyList.size())
            {
                buddyList.get(idx).put("status", buddy.getStatusText());
                buddyListAdapter.notifyDataSetChanged();
                // TODO: selection color/mark is gone after this,
                //       dont know how to return it back.
                //buddyListView.setSelection(buddyListSelectedIdx);
                //buddyListView.performItemClick(buddyListView, buddyListSelectedIdx,
                //							   buddyListView.getItemIdAtPosition(buddyListSelectedIdx));

                /* Return back Call activity */
                notifyCallState(currentCall);
            }

        }
        else if (m.what == MSG_TYPE.REG_STATE)
        {

            String msg_str = (String) m.obj;
            lastRegStatus = msg_str;

        }
        else if (m.what == MSG_TYPE.INCOMING_CALL)
        {

            /* Incoming call */
            final MyCall call = (MyCall) m.obj;
            CallOpParam prm = new CallOpParam();

            /* Only one call at anytime */
            if (currentCall != null)
            {
                prm.setStatusCode(pjsip_status_code.PJSIP_SC_BUSY_HERE);
                try
                {
                    call.hangup(prm);
                }
                catch (Exception e)
                {}
                return true;
            }

            /* Answer with ringing */
            prm.setStatusCode(pjsip_status_code.PJSIP_SC_RINGING);
            try
            {
                call.answer(prm);
            }
            catch
            (Exception e)
            {}

            currentCall = call;
            showCallActivity();

        }
        else
        {
           return false; /* Message not handled */
        }

        return true;
    }


    public void Start()
    {
        try
        {
            /*Endpoint ep  = new Endpoint();
            EpConfig epConfig = new EpConfig();
            TransportConfig sipTpConfig = new TransportConfig();

            ep.libCreate();

            ep.libInit(epConfig);

            sipTpConfig.setPort(5060);
            ep.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_UDP, sipTpConfig);
            ep.libStart();

            AccountConfig acfg = new AccountConfig();
            acfg.setIdUri("sip:4405@10.77.1.61");
            acfg.getRegConfig().setRegistrarUri("sip:10.77.1.61");
            AuthCredInfo cred = new AuthCredInfo("digest", "*", "4405", 0, "");
            acfg.getSipConfig().getAuthCreds().add( cred );
            MyAccount acc = new MyAccount();
            acc.create(acfg);
            //Thread.sleep(10000);
            acc.delete();
            ep.libDestroy();
            ep.delete();*/
        }
        catch (Exception e)
        {
            System.out.println(e);
        }


    }



    public void onClickFive(View view)
    {
        digitStr = "5";
        stringToCallNumber(view,digitStr);
    }

    public void onClickNine(View view)
    {
        digitStr = "9";
        stringToCallNumber(view,digitStr);
    }

    public void onClickSeven(View view)
    {
        digitStr = "7";
        stringToCallNumber(view,digitStr);
    }

    public void onClickOne(View view)
    {
        digitStr = "1";
        stringToCallNumber(view,digitStr);
    }

    public void onClickFour(View view)
    {
        digitStr = "4";
        stringToCallNumber(view,digitStr);
    }

    public void onClickEight(View view)
    {
        digitStr = "8";
        stringToCallNumber(view,digitStr);
    }

    public void onClickTwo(View view)
    {
        digitStr = "2";
        stringToCallNumber(view,digitStr);
    }

    public void onClickThree(View view)
    {
        digitStr = "3";
        stringToCallNumber(view,digitStr);
    }

    public void onClickSix(View view)
    {
        digitStr = "6";
        stringToCallNumber(view,digitStr);
    }

    public void onClickGrill(View view)
    {
        digitStr = "#";
        stringToCallNumber(view,digitStr);
    }

    public void onClickAsterisk(View view)
    {
        digitStr = "*";
        stringToCallNumber(view,digitStr);
    }

    public void onClickZero(View view)
    {
        digitStr = "0";
        stringToCallNumber(view,digitStr);
    }

    public void onClickClear(View view)
    {
        callNumber = removeLastChars(callNumber);
        EditText callNumberEditText = (EditText) findViewById(R.id.CallNumbEditText);
        callNumberEditText.setText(callNumber);
    }

    public void stringToCallNumber(View viev, String numb)
    {
        EditText callNumberEditText = (EditText) findViewById(R.id.CallNumbEditText);
        callNumber+=digitStr;
        callNumberEditText.setText(callNumber);
        //TextView textView = (TextView) findViewById(R.id.textView);
        //textView.setText(callNumber);
    }

    public void onClickCallButton(View view)
    {
        makeCall(view);
    }

    public static String removeLastChars(String str)
    {
        return str.substring(0,str.length()-1);
    }

    public static String modifyUfi(String uri)
    {
        return uri.substring(4,uri.length());
    }



    private static final int REQUEST_PERMISSIONS = 100;
    private static final String PERMISSIONS_REQUIRED[] = new String[]{Manifest.permission.READ_CONTACTS,Manifest.permission.RECORD_AUDIO,Manifest.permission.USE_SIP,Manifest.permission.INTERNET};

    private boolean checkPermission(String permissions[])
    {
        for (String permission : permissions)
        {
            if (ContextCompat.checkSelfPermission(getApplicationContext(), permission) != PackageManager.PERMISSION_GRANTED)
            {
                ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.RECORD_AUDIO}, REQUEST_PERMISSIONS);
                //return false;
            }
        }
        return true;
    }

    private void checkPermissions()
    {
        boolean permissionsGranted = checkPermission(PERMISSIONS_REQUIRED);
        if (permissionsGranted)
        {
            Toast.makeText(this, "You've granted all required permissions!", Toast.LENGTH_SHORT).show();
        }
        else
        {
            boolean showRationale = true;
            for (String permission : PERMISSIONS_REQUIRED)
            {
                showRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, permission);
                if (!showRationale)
                {
                    break;
                }
            }

            String dialogMsg = showRationale ? "We need some permissions to run this APP!" : "You've declined the required permissions, please grant them from your phone settings";

            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Permissions Required")
                    .setMessage(dialogMsg)
                    .setCancelable(false)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which)
                        {
                            ActivityCompat.requestPermissions(MainActivity.this, PERMISSIONS_REQUIRED, REQUEST_PERMISSIONS);
                        }
                    }).create().show();
        }
    }




}