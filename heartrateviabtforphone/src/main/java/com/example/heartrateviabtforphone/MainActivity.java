package com.example.heartrateviabtforphone;

import android.app.Activity;
import android.widget.TextView;

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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        timeTextView = (TextView) findViewById(R.id.timeTextView);
        xTextView = (TextView) findViewById(R.id.xValue);
        yTextView = (TextView) findViewById(R.id.yValue);
        zTextView = (TextView) findViewById(R.id.zValue);
        hrTextView = (TextView) findViewById(R.id.hrValue);
        luxTextView = (TextView) findViewById(R.id.luxValue);
        scTextView = (TextView) findViewById(R.id.scValue);
        ActionBar ab = getActionBar();
        //ab.hide();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

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
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        /*
        if (null != mGoogleApiClient &amp;&amp; mGoogleApiClient.isConnected()) {
            Wearable.MessageApi.removeListener(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
        */
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.my, menu);
        return true;
    }

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

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "onConnected");
        Wearable.MessageApi.addListener(mGoogleApiClient, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended");

    }

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

        TextView tvProcess = findViewById(R.id.tvProcess);
        TextView tvResult = findViewById(R.id.tvResult);
        PostAccess access = new PostAccess(tvProcess, tvResult);
        access.execute("http://www.cc.aoyama.ac.jp/~well-being//HR/wp-content/plugins/well-being/post.php", msg,"");
    }


    private class PostAccess extends AsyncTask<String, String, String> {
        private static final String DEBUG_TAG = "PostAccess";
        private TextView _tvProcess;
        private TextView _tvResult;
        private boolean _success = false;

        public PostAccess(TextView tvProcess, TextView tvResult) {
            _tvProcess = tvProcess;
            _tvResult = tvResult;
        }

        @Override
        public String doInBackground(String... params) {
            String urlStr = params[0];
            String msg = params[1];
            String comment = params[2];

            //String postData = "name= " + name + "&amp;comment=" + comment;
            String postData = "";
            String[] value = msg.split(",", 0);
            String timestamp = "";
            for( int i=0; i<7; i++) {
                if( i==0 ) {
                    timestamp = value[i];
                    continue;
                }
                if( i==1 ) {
                    postData = "d0="+"0,0,"+timestamp+",acc x,"+value[i];
                }
                if( i==2 ) {
                    postData = postData+"&amp;d1="+"0,0,"+timestamp+",acc y,"+value[i];
                }
                if( i==3 ) {
                    postData = postData+"&amp;d2="+"0,0,"+timestamp+",acc z,"+value[i];
                }
                if( i==4 ) {
                    postData = postData+"&amp;d3="+"0,0,"+timestamp+",hr,"+value[i];
                }
                if( i==5 ) {
                    postData = postData+"&amp;d4="+"0,0,"+timestamp+",lux,"+value[i];
                }
                if( i==6 ) {
                    postData = postData+"&amp;d5="+"0,0,"+timestamp+",sc,"+value[i];
                }

            }

            HttpURLConnection con = null;
            InputStream is = null;
            String result = "";

            try {
                //publishProgress(getString(R.string.msg_send_before));
                //publishProgress("message send before");
                URL url = new URL(urlStr);
                con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("POST");
                con.setConnectTimeout(5000);
                con.setReadTimeout(5000);
                con.setDoOutput(true);
                OutputStream os = con.getOutputStream();
                os.write(postData.getBytes());
                os.flush();
                os.close();
                int status = con.getResponseCode();
                if (status != 200) {
                    throw new IOException("ステータスコード: " + status);
                }
                //publishProgress(getString(R.string.msg_send_after));
                //publishProgress("message send after");
                is = con.getInputStream();

                result = is2String(is);
                _success = true;
            }
            catch(SocketTimeoutException ex) {
                //publishProgress(getString(R.string.msg_err_timeout));
                publishProgress("message error timeout: "+ex.toString());
                Log.e(DEBUG_TAG, "タイムアウト", ex);
            }
            catch(MalformedURLException ex) {
                //publishProgress(getString(R.string.msg_err_send));
                publishProgress("message err url malformed: "+ex.toString());
                Log.e(DEBUG_TAG, "URL変換失敗", ex);
            }
            catch(IOException ex) {
                //publishProgress(getString(R.string.msg_err_send));
                publishProgress("message err send: "+ex.toString());
                Log.e(DEBUG_TAG, "通信失敗", ex);
            }
            finally {
                if (con != null) {
                    con.disconnect();
                }
                try {
                    if (is != null) {
                        is.close();
                    }
                }
                catch (IOException ex) {
                    //publishProgress(getString(R.string.msg_err_parse));
                    publishProgress("message err parse"+ex.toString());
                    Log.e(DEBUG_TAG, "InputStream解析失敗", ex);
                }
            }
            return result;
        }

        @Override
        public void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
            /*
            String message = _tvProcess.getText().toString();
            if (!message.equals("")) {
                message += "\n";
            }
            message += values[0];
            _tvProcess.setText(message);
            */
            _tvProcess.setText(values[0]);
        }

        @Override
        public void onPostExecute(String result) {
            if (_success) {
                String status = "";
                String message = "";
                //onProgressUpdate(getString(R.string.msg_parse_before));
                //onProgressUpdate("message parse before");
                try {
                    JSONObject rootJson = new JSONObject(result);
                    status = rootJson.getString("status");
                    message = rootJson.getString("message");
                }
                catch (JSONException ex) {
                    //onProgressUpdate(getString(R.string.msg_err_parse));
                    onProgressUpdate("message err parse:"+ex.toString());
                    Log.e(DEBUG_TAG, "JSON解析失敗", ex);
                }
                //onProgressUpdate(getString(R.string.msg_parse_after));
                onProgressUpdate("status: "+status.toString()+", message: "+message.toString());

                //String message = getString(R.string.dlg_msg_name) + name + "\n" + getString(R.string.dlg_msg_comment) + comment;
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
                message = "Sent "+sdf.format(Calendar.getInstance().getTime());
                String[] msg = _tvResult.getText().toString().split("\n",0);
                for(int i = 0; i<msg.length; i++) {
                    message = message + "\n"+ msg[i];
                    if ( i> 10 ) {
                        break;
                    }
                }

                _tvResult.setText(message);
            }
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
}