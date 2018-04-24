package com.zime.whetherapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.baidu.speech.asr.SpeechConstant;
import com.baidu.tts.chainofresponsibility.logger.LoggerProxy;
import com.baidu.tts.client.SpeechSynthesizer;
import com.baidu.tts.client.SpeechSynthesizerListener;
import com.baidu.tts.client.TtsMode;
import com.zime.whetherapp.control.InitConfig;
import com.zime.whetherapp.control.MyRecognizer;
import com.zime.whetherapp.control.MySyntherizer;
import com.zime.whetherapp.control.NonBlockSyntherizer;
import com.zime.whetherapp.listener.UiMessageListener;
import com.zime.whetherapp.recognization.CityRecogListener;
import com.zime.whetherapp.recognization.MessageStatusRecogListener;
import com.zime.whetherapp.recognization.StatusRecogListener;
import com.zime.whetherapp.recognization.offline.OfflineRecogParams;
import com.zime.whetherapp.util.AutoCheck;
import com.zime.whetherapp.util.OfflineResource;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String RAINING_URL = "https://thumbs.dreamstime.com/t/%E5%8A%A8-%E7%89%87%E4%BA%91%E5%BD%A9%E4%B8%8B%E9%9B%A8-57765254.jpg";

    private static final String SUNNY_URL = "http://reso2.yiihuu.com/779225-z.jpg";

    private static final String WHETHER_URL = "https://www.sojson.com/open/api/weather/json.shtml?city=";

    private Bitmap bitmapWhether = null;

    private TextView txtViewPage = null;

    private ImageView imgViewWhether = null;

    private Handler handler = new Handler(){
        // 消息处理
        public void handleMessage(Message msg){
            Runnable task = null;
            switch (msg.what){
                case MSG_IMAGE:
                    imgViewWhether.setImageBitmap(bitmapWhether);
                    break;
                case MSG_PAGE:
                    String content = (String)msg.getData().get("content");
                    txtViewPage.setText(content);
                    break;
                case MSG_JSON:
                    String strType = (String)msg.getData().get("type");
                    if( strType.equals("晴") ){
                        task = new Runnable() {
                            @Override
                            public void run() {
                                fetchImage(SUNNY_URL);
                            }
                        };
                    }
                    else{
                        task = new Runnable() {
                            @Override
                            public void run() {
                                fetchImage(RAINING_URL);
                            }
                        };
                    }
                    txtViewPage.setText((String)msg.getData().get("notice"));
                    speak(txtViewPage.getText().toString());
                    break;
                case MSG_RECOG:
                    // TODO: 填充文本框，根据识别内容
                    String city = (String)msg.obj;
                    EditText edtTxtUrl = findViewById(R.id.edtTxtUrl);
                    edtTxtUrl.setText(city);
                    break;
                default:
                    break;
            }

            if( task != null ){
                Thread fetchImgThread = new Thread(task);
                fetchImgThread.start();
            }
        }
    };

    public static final int MSG_PAGE = 1;

    public static final int MSG_IMAGE = 2;

    public static final int MSG_JSON = 3;

    public static final int MSG_RECOG = 4;

    /* 语音合成部分---start */

    // ================== 初始化参数设置开始 ==========================
    /**
     * 发布时请替换成自己申请的appId appKey 和 secretKey。注意如果需要离线合成功能,请在您申请的应用中填写包名。
     * 本demo的包名是com.baidu.tts.sample，定义在build.gradle中。
     */
    protected String appId = "11005757";

    protected String appKey = "Ovcz19MGzIKoDDb3IsFFncG1";

    protected String secretKey = "e72ebb6d43387fc7f85205ca7e6706e2";

    // TtsMode.MIX; 离在线融合，在线优先； TtsMode.ONLINE 纯在线； 没有纯离线
    protected TtsMode ttsMode = TtsMode.MIX;

    // 离线发音选择，VOICE_FEMALE即为离线女声发音。
    // assets目录下bd_etts_common_speech_m15_mand_eng_high_am-mix_v3.0.0_20170505.dat为离线男声模型；
    // assets目录下bd_etts_common_speech_f7_mand_eng_high_am-mix_v3.0.0_20170512.dat为离线女声模型
    protected String offlineVoice = OfflineResource.VOICE_MALE;

    // ===============初始化参数设置完毕，更多合成参数请至getParams()方法中设置 =================

    // 主控制类，所有合成控制方法从这个类开始
    protected MySyntherizer synthesizer;

    private static final String TAG = "SynthActivity";

    protected Handler mainHandler = new Handler() {
        /*
         * @param msg
         */
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }

    };
    /* 语音合成部分---end */

    /* 语音识别 --- start */

    /**
     * 识别控制器，使用MyRecognizer控制识别的流程
     */
    protected MyRecognizer myRecognizer;

    /*
     * 本Activity中是否需要调用离线命令词功能。根据此参数，判断是否需要调用SDK的ASR_KWS_LOAD_ENGINE事件
     */
    protected boolean enableOffline = false;

    /* 语音识别 --- end */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtViewPage = findViewById(R.id.txtViewPage);
        imgViewWhether = findViewById(R.id.imgWhether);
        Button btnBrowse = findViewById(R.id.btnBrowse);
        btnBrowse.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View view) {
                EditText edtUrl = findViewById(R.id.edtTxtUrl);
                final String url = edtUrl.getText().toString();
                Runnable task = new Runnable() {
                    @Override
                    public void run() {
                        fetchUrlContent(url);
                    }
                };

                Thread urlThread = new Thread(task);
                urlThread.start();
            }
        });

        Runnable task = new Runnable() {
            @Override
            public void run() {
                fetchWhether("贵阳");
            }
        };
        Thread thread = new Thread(task);
        thread.start();

        Button btnBluetooth = findViewById(R.id.btnBlueTooth);
        btnBluetooth.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, BluetoothActivity.class);
                MainActivity.this.startActivity(intent);
            }
        });

        Button btnRecog = findViewById(R.id.btnRecog);
        btnRecog.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View view) {
                startRough();
            }
        });

        initPermission();
        initialTts();
        initRecog();
    }

    /**
     * 在onCreate中调用。初始化识别控制类MyRecognizer
     */
    protected void initRecog() {
        StatusRecogListener listener = new MessageStatusRecogListener(handler);
        myRecognizer = new MyRecognizer(this, listener);

        if (enableOffline) {
            myRecognizer.loadOfflineEngine(OfflineRecogParams.fetchOfflineParams());
        }
    }

    /**
     * 测试demo成功后可以修改这个方法
     * 粗略测试，将原来的start方法注释，这个方法改为start即可。
     * 点击开始按钮使用，注意此时与本demo的UI已经解绑，UI上不会显示，请自行看logcat日志
     */
    protected void startRough() {
        // initRecog中已经初始化，这里释放。不需要集成到您的代码中
        myRecognizer.release();
        myRecognizer = null;
        // 上面不需要集成到您的代码中

        /*********************************************/
        // 1. 确定识别参数
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put(SpeechConstant.ACCEPT_AUDIO_VOLUME, false);
        // 具体的params的值在 测试demo成功后，myRecognizer.start(params);中打印

        // 2. 初始化IRecogListener
        StatusRecogListener listener = new CityRecogListener(handler);
        // 日志显示在logcat里，UI界面上是没有的。需要显示在界面上， 这里设置为handler

        // 3 初始化 MyRecognizer
        myRecognizer = new MyRecognizer(this, listener);

        // 4. 启动识别
        myRecognizer.start(params);
        // 日志显示在logcat里，UI界面上是没有的。

        // 5 识别结束了别忘了释放。

        // 需要离线识别过程，需要加上 myRecognizer.loadOfflineEngine(OfflineRecogParams.fetchOfflineParams());
        // 注意这个loadOfflineEngine是异步的， 不能连着调用 start
    }

    /**
     * 销毁时需要释放识别资源。
     */
    @Override
    protected void onDestroy() {
        myRecognizer.release();
        Log.i(TAG, "onDestory");
        super.onDestroy();
    }

    /**
     * android 6.0 以上需要动态申请权限
     */
    private void initPermission() {
        String[] permissions = {
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.MODIFY_AUDIO_SETTINGS,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_SETTINGS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.RECORD_AUDIO
        };

        ArrayList<String> toApplyList = new ArrayList<String>();

        for (String perm : permissions) {
            if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(this, perm)) {
                toApplyList.add(perm);
                // 进入到这里代表没有权限.
            }
        }
        String[] tmpList = new String[toApplyList.size()];
        if (!toApplyList.isEmpty()) {
            ActivityCompat.requestPermissions(this, toApplyList.toArray(tmpList), 123);
        }

    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        // 此处为android 6.0以上动态授权的回调，用户自行实现。
    }

    protected void initialTts() {
        LoggerProxy.printable(true); // 日志打印在logcat中
        // 设置初始化参数
        // 此处可以改为 含有您业务逻辑的SpeechSynthesizerListener的实现类
        SpeechSynthesizerListener listener = new UiMessageListener(mainHandler);

        Map<String, String> params = getParams();


        // appId appKey secretKey 网站上您申请的应用获取。注意使用离线合成功能的话，需要应用中填写您app的包名。包名在build.gradle中获取。
        InitConfig initConfig = new InitConfig(appId, appKey, secretKey, ttsMode, params, listener);

        // 如果您集成中出错，请将下面一段代码放在和demo中相同的位置，并复制InitConfig 和 AutoCheck到您的项目中
        // 上线时请删除AutoCheck的调用
        AutoCheck.getInstance(getApplicationContext()).check(initConfig, new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == 100) {
                    AutoCheck autoCheck = (AutoCheck) msg.obj;
                    synchronized (autoCheck) {
                        String message = autoCheck.obtainDebugMessage();
                        Log.w("AutoCheckMessage", message);
                    }
                }
            }

        });
        synthesizer = new NonBlockSyntherizer(this, initConfig, mainHandler); // 此处可以改为MySyntherizer 了解调用过程
    }

    /**
     * 合成的参数，可以初始化时填写，也可以在合成前设置。
     *
     * @return
     */
    protected Map<String, String> getParams() {
        Map<String, String> params = new HashMap<String, String>();
        // 以下参数均为选填
        // 设置在线发声音人： 0 普通女声（默认） 1 普通男声 2 特别男声 3 情感男声<度逍遥> 4 情感儿童声<度丫丫>
        params.put(SpeechSynthesizer.PARAM_SPEAKER, "0");
        // 设置合成的音量，0-9 ，默认 5
        params.put(SpeechSynthesizer.PARAM_VOLUME, "9");
        // 设置合成的语速，0-9 ，默认 5
        params.put(SpeechSynthesizer.PARAM_SPEED, "5");
        // 设置合成的语调，0-9 ，默认 5
        params.put(SpeechSynthesizer.PARAM_PITCH, "5");

        params.put(SpeechSynthesizer.PARAM_MIX_MODE, SpeechSynthesizer.MIX_MODE_DEFAULT);
        // 该参数设置为TtsMode.MIX生效。即纯在线模式不生效。
        // MIX_MODE_DEFAULT 默认 ，wifi状态下使用在线，非wifi离线。在线状态下，请求超时6s自动转离线
        // MIX_MODE_HIGH_SPEED_SYNTHESIZE_WIFI wifi状态下使用在线，非wifi离线。在线状态下， 请求超时1.2s自动转离线
        // MIX_MODE_HIGH_SPEED_NETWORK ， 3G 4G wifi状态下使用在线，其它状态离线。在线状态下，请求超时1.2s自动转离线
        // MIX_MODE_HIGH_SPEED_SYNTHESIZE, 2G 3G 4G wifi状态下使用在线，其它状态离线。在线状态下，请求超时1.2s自动转离线

        // 离线资源文件， 从assets目录中复制到临时目录，需要在initTTs方法前完成
        OfflineResource offlineResource = createOfflineResource(offlineVoice);
        // 声学模型文件路径 (离线引擎使用), 请确认下面两个文件存在
        params.put(SpeechSynthesizer.PARAM_TTS_TEXT_MODEL_FILE, offlineResource.getTextFilename());
        params.put(SpeechSynthesizer.PARAM_TTS_SPEECH_MODEL_FILE,
                offlineResource.getModelFilename());
        return params;
    }

    protected OfflineResource createOfflineResource(String voiceType) {
        OfflineResource offlineResource = null;
        try {
            offlineResource = new OfflineResource(this, voiceType);
        } catch (IOException e) {
            // IO 错误自行处理
            e.printStackTrace();
            Log.e(TAG,"【error】:copy files from assets failed." + e.getMessage());
        }
        return offlineResource;
    }

    /**
     * speak 实际上是调用 synthesize后，获取音频流，然后播放。
     * 获取音频流的方式见SaveFileActivity及FileSaveListener
     * 需要合成的文本text的长度不能超过1024个GBK字节。
     */
    private void speak(String text) {
        int result = synthesizer.speak(text);
        checkResult(result, "speak");
    }

    private void checkResult(int result, String method) {
        if (result != 0) {
            Log.e(TAG,"error code :" + result + " method:" + method + ", 错误码文档:http://yuyin.baidu.com/docs/tts/122 ");
        }
    }

    private void fetchImage(String url){
        // 1. 获取INTERNET权限
        if(ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.INTERNET) !=
                PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.INTERNET}, 10);
            return;
        }

        // 2. 打开链接，获取图片
        try{
            URL urlObj = new URL(url);
            InputStream is = urlObj.openStream();
            bitmapWhether = BitmapFactory.decodeStream(is);

            // 3. 通知界面，更新
            handler.sendEmptyMessage(MSG_IMAGE);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    private void fetchWhether(String strCity){
        // 1. 获取INTERNET权限
        if(ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.INTERNET) !=
                PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.INTERNET}, 10);
            return;
        }

        //2. 获取天气预报的字符串
        HttpGet request = new HttpGet(WHETHER_URL + strCity);

        // 3. 构建HttpClient对象
        HttpClient client = new DefaultHttpClient();

        try {
            // 4. 执行HTTP-GET请求，获取到HttpResponse应答
            HttpResponse response = client.execute(request);

            // 5. 从应答中取到字符串/内容
            // Java流
            InputStream is = response.getEntity().getContent();
            BufferedReader rd = new BufferedReader(new InputStreamReader(is));
            String content = ""; // 所有内容
            String line = "";    // 每一行的内容
            while( (line = rd.readLine()) != null ){
                content = content + line;
            }

            JSONObject jsonObject = new JSONObject(content);
            jsonObject.get("status");
            if( jsonObject.get("status").equals(200) ){
                JSONArray array = jsonObject.getJSONObject("data").getJSONArray("forecast");
                JSONObject obj1 = array.getJSONObject(0);

                JSONObject data = jsonObject.getJSONObject("data");
                int pm25 = data.getInt("pm25");
                JSONObject yesterday = data.getJSONObject("yesterday");
                String yesterdayHigh123 = yesterday.getString("high");

                // 6. 更新界面
                // 线程、处理器(Handler)、消息三个概念
                Message msg = new Message();
                msg.what = MSG_JSON;
                Bundle bundle = new Bundle();
                bundle.putString("type", obj1.getString("type"));
                bundle.putString("notice", obj1.getString("notice"));
                msg.setData(bundle);
                handler.sendMessage(msg);
            }

        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    private void fetchUrlContent(String strUrl){   // sendHttpComm
        // 1. 获取INTERNET权限
        if(ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.INTERNET) !=
                PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.INTERNET}, 10);
            return;
        }

        // 2. 构建获取百度的Request
        HttpGet request = new HttpGet(strUrl);

        // 3. 构建HttpClient对象
        HttpClient client = new DefaultHttpClient();

        try {
            // 4. 执行HTTP-GET请求，获取到HttpResponse应答
            HttpResponse response = client.execute(request);

            // 5. 从应答中取到字符串/内容
            // Java流
            InputStream is = response.getEntity().getContent();
            BufferedReader rd = new BufferedReader(new InputStreamReader(is));
            String content = ""; // 所有内容
            String line = "";    // 每一行的内容
            while( (line = rd.readLine()) != null ){
                content = content + line;
            }

            // 6. 更新界面
            // 线程、处理器(Handler)、消息三个概念
            Message msg = new Message();
            msg.what = MSG_PAGE;
            Bundle bundle = new Bundle();
            bundle.putString("content", content);
            msg.setData(bundle);
            handler.sendMessage(msg);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
}
