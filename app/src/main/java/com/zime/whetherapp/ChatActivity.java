package com.zime.whetherapp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by sony on 2018-04-10.
 */

public class ChatActivity extends AppCompatActivity {
    private ArrayAdapter<String> chatMsgAdapter = null;

    private List<String> msgList = new ArrayList<>();

    private Button btnSend;

    private Button btnDisconnect;

    private EditText edtTxtMsg;

    private ListView lstViewMsgs;

    private BluetoothServerSocket serverSocket = null;

    private BluetoothSocket socket = null;

    private BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    private BluetoothDevice bluetoothDevice = null;

    private static final String SERVER_CLIENT_UUID = "cda3eba5-b683-4148-8e16-ad5c1fa89ed7";

    private static final String SERVER_NAME = "bleApp";

    private ServerThread serverThread;

    private ClientThread clientThread;

    private ReadThread readThread;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        initUI();
    }

    private void initUI(){
        chatMsgAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, msgList);
        lstViewMsgs = findViewById(R.id.lstViewChatContent);
        lstViewMsgs.setAdapter(chatMsgAdapter);
        lstViewMsgs.setFastScrollEnabled(true);

        edtTxtMsg = findViewById(R.id.edtTxtMsg);
        edtTxtMsg.clearFocus();

        btnSend = findViewById(R.id.btnSendMsg);
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String msg = edtTxtMsg.getText().toString();
                if( msg.length() > 0 ){
                    sendMessage(msg);

                    edtTxtMsg.setText("");
                    edtTxtMsg.clearFocus();

                    InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(edtTxtMsg.getWindowToken(), 0);
                }
                else{
                    Toast.makeText(ChatActivity.this, "请输入消息内容", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnDisconnect = findViewById(R.id.btnDisconnect);
        btnDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if( BluetoothUtil.isServer ){
                    shutdownServer();
                }
                else{
                    shutdownClient();
                }

                BluetoothUtil.isOpen = false;
                Toast.makeText(ChatActivity.this, "已断开连接！", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendMessage(String msg){
        if( socket == null ){
            Toast.makeText(this, "蓝牙没有连接", Toast.LENGTH_SHORT).show();

            return;
        }

        try {
            OutputStream os = socket.getOutputStream();
            os.write(msg.getBytes());
        }
        catch (IOException e){
            e.printStackTrace();
        }

        msgList.add(msg);
        chatMsgAdapter.notifyDataSetChanged();
        lstViewMsgs.setSelection(msgList.size() - 1);
    }

    @Override
    protected void onPostResume() {
        // 如果你作为服务器，请加下面一行
        BluetoothUtil.isServer = true;

        if( BluetoothUtil.isOpen ){
            Toast.makeText(this, "连接已经打开，可以直接通信。如需重连，请先断开连接！", Toast.LENGTH_SHORT).show();
        }
        else {
            if (BluetoothUtil.isServer) {
                serverThread = new ServerThread();
                serverThread.start();
                BluetoothUtil.isOpen = true;
            } else {
                if (BluetoothUtil.bluetoothAddress == null) {
                    Toast.makeText(this, "蓝牙地址为空！", Toast.LENGTH_SHORT).show();
                } else {
                    bluetoothDevice = bluetoothAdapter.getRemoteDevice(BluetoothUtil.bluetoothAddress);
                    clientThread = new ClientThread();
                    clientThread.start();
                    BluetoothUtil.isOpen = true;
                }
            }
        }
        super.onPostResume();
    }

    private Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            msgList.add((String)msg.obj);
            chatMsgAdapter.notifyDataSetChanged();
            lstViewMsgs.setSelection(msgList.size() - 1);
        }
    };

    private void shutdownServer(){
        new Thread(){
            @Override
            public void run() {
                if( serverThread != null ){
                    serverThread.interrupt();
                    serverThread = null;
                }

                if( readThread != null ){
                    readThread.interrupt();
                    readThread = null;
                }

                try{
                    if( socket != null ){
                        socket.close();
                        socket = null;
                    }

                    if( serverSocket != null ){
                        serverSocket.close();
                        serverSocket = null;
                    }
                }
                catch (IOException e){
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private void shutdownClient(){
        new Thread(){
            @Override
            public void run() {
                if( clientThread != null ){
                    clientThread.interrupt();
                    clientThread = null;
                }

                if( readThread != null ){
                    readThread.interrupt();
                    readThread = null;
                }

                try{
                    if( socket != null ){
                        socket.close();
                        socket = null;
                    }
                }
                catch (IOException e){
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private class ClientThread extends Thread{
        @Override
        public void run() {
            try {
                socket = bluetoothDevice.createRfcommSocketToServiceRecord(UUID.fromString(SERVER_CLIENT_UUID));
                // 通知界面，等待客户端连接中
                Message msg = new Message();
                msg.obj = "客户端连接服务器中:" + BluetoothUtil.bluetoothAddress;
                msg.what = 0;
                handler.sendMessage(msg);

                socket.connect();
                msg.obj = "已经连接上服务器！可以发送消息";
                msg.what = 0;
                handler.sendMessage(msg);

                //启动接收线程，从服务器端接收消息
                readThread = new ReadThread();
                readThread.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ServerThread extends Thread{
        @Override
        public void run() {
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVER_NAME,
                        UUID.fromString(SERVER_CLIENT_UUID));


                // 通知界面，等待客户端连接中
                Message msg = new Message();
                msg.obj = "等待客户端连接中...";
                msg.what = 0;
                handler.sendMessage(msg);

                socket = serverSocket.accept();
                msg.obj = "客户端已经连接！可以发送消息";
                msg.what = 0;
                handler.sendMessage(msg);

                // 启动线程接收消息
                readThread = new ReadThread();
                readThread.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ReadThread extends Thread{
        @Override
        public void run() {
            byte[] buf = new byte[1024];
            int len = 0;
            InputStream is = null;

            try {
                is = socket.getInputStream();
            }
            catch (IOException e){
                e.printStackTrace();
            }

            while (true){
                try{
                    if( (len = is.read(buf)) > 0 ){
                        byte[] readed = new byte[len];
                        System.arraycopy(buf, 0, readed, 0, len);
                        String s = new String(readed);
                        Message msg = new Message();
                        msg.obj = s;
                        msg.what = 1;
                        handler.sendMessage(msg);
                    }
                }
                catch (IOException e){
                    try{
                        is.close();
                    }
                    catch (Exception e1){
                        e1.printStackTrace();
                    }
                    break;
                }
            }
        }
    }
}
