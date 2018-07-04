package com.zime.whetherapp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by sony on 2018-04-01.
 */

public class BluetoothActivity extends AppCompatActivity {
    private ListView deviceListView = null;

    private ArrayAdapter<String> adapter = null;

    private List<String> deviceList = new ArrayList<>();

    private Button btnSearch = null;

    private BluetoothAdapter bluetoothAdapter = null;

    private DeviceReceiver mydevice = new DeviceReceiver();

    private boolean hasRegistered = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        initView();
        initBluetooth();
    }

    @Override
    protected void onStart() {
        if( !hasRegistered ) {
            hasRegistered = true;
            IntentFilter filterStart = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            IntentFilter filterEnd = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            registerReceiver(mydevice, filterStart);
            registerReceiver(mydevice, filterEnd);
        }
        super.onStart();
    }

    private void initView(){
        deviceListView = findViewById(R.id.lstBluetooth);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, deviceList);
        deviceListView.setAdapter(adapter);
        deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                // 1) 弹出确认对话框
                final String bluetoothInfo = deviceList.get(i);

                if( bluetoothAdapter != null && bluetoothAdapter.isDiscovering() ){
                    bluetoothAdapter.cancelDiscovery();
                    btnSearch.setText("继续搜索");
                }

                AlertDialog.Builder dlg = new AlertDialog.Builder(BluetoothActivity.this);
                dlg.setTitle("确定连接该设备");
                dlg.setMessage(bluetoothInfo);

                // 2) 根据用户选择，跳转Activity或者保留在当前界面上
                dlg.setPositiveButton("连接", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        boolean isCancled = bluetoothAdapter.cancelDiscovery();
                        if( !isCancled ){
                            Log.e("BluetoothActivity", "Failed to cancel discovery!");
                        }

                        BluetoothUtil.bluetoothAddress = bluetoothInfo.substring(bluetoothInfo.length() - 17);
                        Intent intent = new Intent(BluetoothActivity.this, ChatActivity.class);
                        BluetoothActivity.this.startActivity(intent);
                    }
                });

                dlg.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        BluetoothUtil.bluetoothAddress = null;
                    }
                });
                dlg.show();
            }
        });

        btnSearch = findViewById(R.id.btnSearch);
        btnSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if( bluetoothAdapter.isDiscovering() ){
                    bluetoothAdapter.cancelDiscovery();
                    btnSearch.setText("继续搜索");
                }
                else{
                    getAvailableDevices();
                    bluetoothAdapter.startDiscovery();
                    btnSearch.setText("停止搜索");
                }
            }
        });
    }

    private void initBluetooth(){
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if( bluetoothAdapter != null ){
            if( !bluetoothAdapter.isEnabled() ){
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(intent, RESULT_FIRST_USER);

                Intent intent1 = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 200);
                startActivity(intent1);

                bluetoothAdapter.enable();
            }
        }
        else{
            AlertDialog.Builder dialog = new AlertDialog.Builder(this);
            dialog.setTitle("没有蓝牙设备");
            dialog.setMessage("不支持蓝牙设备，请检查");

            dialog.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {

                }
            });
            dialog.show();
        }
    }

    private void getAvailableDevices(){
        Set<BluetoothDevice> devices = bluetoothAdapter.getBondedDevices();

        if( bluetoothAdapter != null && bluetoothAdapter.isDiscovering() ){
            deviceList.clear();
            adapter.notifyDataSetChanged();
        }
        else{
            if( devices.size() > 0 ){
                for(BluetoothDevice device : devices){
                    if(!deviceList.contains(device.getName() + '\n' + device.getAddress())) {
                        deviceList.add(device.getName() + '\n' + device.getAddress());
                    }
                }
                adapter.notifyDataSetChanged();
            }
            else{
                deviceList.add("没有找到匹配的蓝牙");
                adapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (resultCode){
            case RESULT_OK:
                getAvailableDevices();
                break;
            case RESULT_CANCELED:
                break;
            default:
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private class DeviceReceiver extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO: 对接收的广播消息做处理
            String action = intent.getAction();

            if( BluetoothDevice.ACTION_FOUND.equals(action) ){
                // 搜索到新的设备
                BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if( bluetoothDevice.getBondState() != BluetoothDevice.BOND_BONDED ){
                    // 未配对
                    deviceList.add(bluetoothDevice.getName() + '\n' + bluetoothDevice.getAddress());
                    adapter.notifyDataSetChanged();
                }
            }
            else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
                // 搜索已经结束

                if( deviceListView.getCount() == 0 ){
                    deviceList.add("没有蓝牙设备");
                    adapter.notifyDataSetChanged();
                }
                Log.i("BluetoothActivity", "Discovery finished!");
                btnSearch.setText("继续搜索");
            }
        }
    }
}
