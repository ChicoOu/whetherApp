package com.zime.whetherapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

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
