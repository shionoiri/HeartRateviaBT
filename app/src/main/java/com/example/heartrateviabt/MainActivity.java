package com.example.heartrateviabt;


import android.app.Activity;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class MainActivity extends Activity {
    private TextView mTextView;
    public ComponentName mServiceName;
    private final String TAG = MainActivity.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //アプリ使用中は画面ついたままにする
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mTextView = findViewById(R.id.text);
        mTextView.setTextSize(14.0f);

        //ファイルの作成
        CtrlFile ctrlfile = new CtrlFile();
        if(ctrlfile.checkfile()) {
            mTextView.setText("Running.");
        }else{
            mTextView.setText("Stopped.");
        }

        //ComponentNameはIntentの一部，どのコンポーネントを起動するかを指定する．今回は，SensorJobService
        mServiceName = new ComponentName(this, SensorJobService.class);
        //以下，開始ボタン押した後の処理
        findViewById(R.id.start_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                CtrlFile file = new CtrlFile();
                if( file.checkfile() ) {
                    mTextView.setText("Already running");
                    return;
                }

                JobScheduler scheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
                for (int i = 0; i < 1; i++) {
                    JobInfo jobInfo = new JobInfo.Builder(i, mServiceName)
                            //実行時間を1秒遅らせる
                            .setMinimumLatency(1000)
                            //ジョブの最大遅延時間60秒
                            .setOverrideDeadline(60000)
                            //端末が再起動したときJobを再起動するか否かを設定．Trueを設定した場合RECEIVE_BOOT_COMPLETEDを指定
                            .setPersisted(true)
                            //ネットワークが有効のときに実行する
                            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                            .build();
                    scheduler.schedule(jobInfo);
                }

                file.createfile();
                mTextView.setText("Running");

            }
        });
        //停止ボタン押した後の処理
        findViewById(R.id.stop_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CtrlFile file = new CtrlFile();
                file.deletefile();
                mTextView.setText("Stopped");
                JobScheduler scheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
                scheduler.cancelAll();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

}

class SensorJobService extends JobService {
    private final String TAG = MainActivity.class.getName();

    @Override
    //登録したジョブが実行されると呼び出されるメソッド．このメソッドが終了時点で処理が続いているのならTrueを返し，処理の最後にjobFinishedを呼び出す必要あり．
    public boolean onStartJob(JobParameters params) {
        new ToastTask().execute(params);
        new SensorWorker().execute(params);

        CtrlFile file = new CtrlFile();
        if( !file.checkfile() ) {
            return true;
        }
        ComponentName mServiceName = new ComponentName(this, SensorJobService.class);

        JobScheduler scheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
        for (int i = 0; i < 1; i++) {
            JobInfo jobInfo = new JobInfo.Builder(i, mServiceName)
                    .setMinimumLatency(300*1000)
                    .setOverrideDeadline(180*1000)
                    .setPersisted(true)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .build();
            scheduler.schedule(jobInfo);
        }
        return true;
    }

    @Override
    //処理実行中に条件を満たせず，中断した場合に呼び出されるメソッド．このメソッド内でjobFinishedを呼び出す必要あり．falseの場合ジョブは終了．
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    private class ToastTask extends AsyncTask<JobParameters, Void, String> {

        protected JobParameters mJobParam;

        @Override
        protected void onPreExecute() {

        }

        @Override
        //別スレッドでの処理
        protected String doInBackground(JobParameters... params) {
            mJobParam = params[0];
            return String.valueOf(mJobParam.getJobId());
        }

        @Override
        //doInBackgroundメソッドの戻り値をこのメソッドの引数として受け取り，その結果を画面に反映できる
        protected void onPostExecute(String result) {
            jobFinished(mJobParam, false);
        }
    }

    private class SensorWorker extends AsyncTask<JobParameters,Void,String> implements SensorEventListener {

        private final String TAG = MainActivity.class.getName();
        protected JobParameters mJobParam;

        private GoogleApiClient mGoogleApiClient;
        private SensorManager mSensorManager;
        private String mNode;

        private float x = 0, y = 0, z = 0;
        private int hr = 10;
        private float gyx = 0, gyy = 0, gyz = 0;
        private float lux = 100.0f;
        private int sc = 0;
        private int kcal = 0;
        int count = 0;

        @Override
        protected void onPreExecute() {
        }

        @Override
        //addConnectionCallbackまでがインスタンス化と考えてよさそう
        protected String doInBackground(JobParameters... params) {
            mGoogleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                        @Override
                        // GoogleApiClientの接続に成功したときに呼ばれる
                        public void onConnected(Bundle bundle) {
                            Log.d(TAG, "onConnected");

                            Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                                @Override
                                public void onResult(NodeApi.GetConnectedNodesResult nodes) {
                                    //通信可能なnodeが複数ある場合も考慮
                                    if (nodes.getNodes().size() > 0) {
                                        mNode = nodes.getNodes().get(0).getId();

                                    }
                                }
                            });
                        }

                        @Override
                        // 接続が中断したときに呼ばれる
                        public void onConnectionSuspended(int i) {
                            Log.d(TAG, "onConnectionSuspended");

                        }
                    })
                    .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                        @Override
                        // 接続に失敗したとき
                        public void onConnectionFailed(ConnectionResult connectionResult) {
                            Log.d(TAG, "onConnectionFailed : " + connectionResult.toString());
                        }
                    })
                    .build();

            //接続
            mGoogleApiClient.connect();

            //センサーマネージャーのインスタンスを取得
            mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
            //センサーマネージャーのgetDefaultSensor()でセンサー制御のインスタンスを取得
            Sensor sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            //イベントリスナー，センサ―の種類，精度の指定．
            mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL*5);

            Sensor gySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            mSensorManager.registerListener(this, gySensor, SensorManager.SENSOR_DELAY_NORMAL*5);

            try {
                //3秒間スレッドを止めてonSensorChanged()が呼び出され値が取得できるように期待．
                Thread.sleep(3*1000);
                //wait();
            }catch(Exception e) {

            }
            //ここで登録解除
            mSensorManager.unregisterListener(this);

            Sensor hrSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
            mSensorManager.registerListener(this, hrSensor, SensorManager.SENSOR_DELAY_NORMAL);

            //Sensor hbSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_BEAT);
            //mSensorManager.registerListener(this, hbSensor, SensorManager.SENSOR_DELAY_NORMAL);

            //TYPE_STEP_COUNTERは歩数計
            Sensor scSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            mSensorManager.registerListener(this, scSensor, SensorManager.SENSOR_DELAY_NORMAL);
            //照度センサー
            Sensor luxSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            mSensorManager.registerListener(this, luxSensor, SensorManager.SENSOR_DELAY_NORMAL);
            //おそらく消費カロリーに関するセンサー？
            Sensor kcalSensor = mSensorManager.getDefaultSensor(65548);
            mSensorManager.registerListener(this, kcalSensor, SensorManager.SENSOR_DELAY_NORMAL);

            int i=0;
            while( i < 3 ) {
                //心拍数が10以上かつ，照度0以上
                if( hr > 10 && lux != 0 ){
                    break;
                }
                try {
                    Thread.sleep(10*1000);
                    //wait();
                }catch(Exception e) {

                }
                i++;

            }
            //
            mJobParam = params[0];
            return String.valueOf(mJobParam.getJobId());
        }

        @Override
        protected void onPostExecute(String result) {
            mSensorManager.unregisterListener(this);
            //日時や時間
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
            //sdfと一緒にセンサーデータも送信
            String SEND_DATA = sdf.format(Calendar.getInstance().getTime()) + "," + x + "," + y + "," + z + "," + hr + "," + lux + "," + sc;
            //Nodeがnullでない場合，送信
            if (mNode != null) {
                Wearable.MessageApi.sendMessage(mGoogleApiClient, mNode, SEND_DATA, null).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                    @Override
                    //エラー処理
                    public void onResult(MessageApi.SendMessageResult result) {
                        if (!result.getStatus().isSuccess()) {
                            Log.d(TAG, "ERROR : failed to send Message" + result.getStatus());
                        }
                    }
                });
            }

            //SensorWorkerのジョブはここで終了
            jobFinished(mJobParam, false);
        }
        //以下，センサーの値変化，精度変化について
        @Override
        public void onSensorChanged(SensorEvent event) {

            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                x = event.values[0];
                y = event.values[1];
                z = event.values[2];
            }

            else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                gyx = event.values[0];
                gyy = event.values[1];
                gyz = event.values[2];
            }

            else if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
                lux = event.values[0];
            }

            else if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
                int h = hr;
                hr =(int)event.values[0];
                if( hr == 255 ){
                    hr = h;
                }
            }

            else if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
                sc = (int) event.values[0];
            }

            else if (event.sensor.getType() == 65548) {
                kcal = (int) event.values[0];
            }

        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

    }
}

class CtrlFile {
    private String filename = "/data/data/net..mtl.healthinfo/log.txt";
    //ファイルがあるか確認
    public boolean checkfile() {
        File file = new File(filename);
        return( file.exists() );
    }
    //ファイルへの書き込み
    public boolean createfile() {
        FileOutputStream fos;
        try {
            //fos = openFileOutput(fileName, Context.MODE_PRIVATE);
            fos = new FileOutputStream(new File(filename));
            fos.write(new Date().toString().getBytes());
        } catch (IOException e) {
            String path = new File(".").getAbsoluteFile().getParent();
            e.printStackTrace();
            return(false);
        }
        return(true);
    }
    //ファイルの削除
    public boolean deletefile() {
        File file = new File(filename);
        return(file.delete());
    }
}