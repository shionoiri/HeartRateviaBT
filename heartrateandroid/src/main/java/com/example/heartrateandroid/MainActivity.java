package com.example.heartrateandroid;

import android.app.ActionBar;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class MainActivity extends Activity implements GoogleApiClient.ConnectionCallbacks, MessageApi.MessageListener {
    private static final String TAG = MainActivity.class.getName();
    private GoogleApiClient mGoogleApiClient;

    TextView timeTextView;
    TextView xTextView;
    TextView yTextView;
    TextView zTextView;
    TextView hrTextView;
    TextView luxTextView;
    TextView scTextView;
    int x, y, z;

    String[] names = new String[]{"x-value", "y-value", "z-value", "hr"};
    int[] colors = new int[]{Color.RED, Color.GREEN, Color.BLUE, Color.CYAN};

    @Override
    //アプリ開始(画面表示前)
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //それぞれ受信するデータのテキストビューを入手
        timeTextView = (TextView) findViewById(R.id.timeTextView);
        xTextView = (TextView) findViewById(R.id.xValue);
        yTextView = (TextView) findViewById(R.id.yValue);
        zTextView = (TextView) findViewById(R.id.zValue);
        hrTextView = (TextView) findViewById(R.id.hrValue);
        luxTextView = (TextView) findViewById(R.id.luxValue);
        scTextView = (TextView) findViewById(R.id.scValue);
        //アクションバーの追加
        ActionBar ab = getActionBar();
        //例のごとくアプリ起動中の画面表示維持
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        //Bluetooth接続にGoogleApiClientを用いるためにここでインスタンス化．また接続ミスったときの処理もここで記述
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult connectionResult) {
                        Log.d(TAG, "onConnectionFailed:" + connectionResult.toString());
                    }
                })
                .addApi(Wearable.API)
                .build();

    }

    @Override
    //アクティビティー開始
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    //アクティビティ停止
    protected void onStop() {
        super.onStop();
        /*
        if (null != mGoogleApiClient &amp;&amp; mGoogleApiClient.isConnected()) {
            Wearable.MessageApi.removeListener(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
        */
    }
    //Android画面内にあるオプションメニューの作成
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.my, menu);
        return true;
    }
    //オプションメニューが選択されたときの処理
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        //if (id == R.id.action_settings) {
        //    return true;
        //}
        return super.onOptionsItemSelected(item);
    }
    //接続時
    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "onConnected");
        Wearable.MessageApi.addListener(mGoogleApiClient, this);
    }
    //切断された時の処理
    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended");

    }
    //メッセージ受信
    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        //xTextView.setText(messageEvent.getPath());
        String msg = messageEvent.getPath();
        String[] value = msg.split(",", 0);

        timeTextView.setText("time: "+String.valueOf(value[0]));
        xTextView.setText("acc x: "+String.valueOf(value[1]));
        yTextView.setText("acc y: "+String.valueOf(value[2]));
        zTextView.setText("acc z: "+String.valueOf(value[3]));
        hrTextView.setText("hb: "+String.valueOf(value[4]));
        luxTextView.setText("lux: "+String.valueOf(value[5]));
        scTextView.setText("sc: "+String.valueOf(value[6]));
    }


        private String is2String(InputStream is) throws IOException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuffer sb = new StringBuffer();
            char[] b = new char[1024];
            int line;
            while(0 <= (line = reader.read(b))) {
                sb.append(b, 0, line);
            }
            return sb.toString();
        }
    }