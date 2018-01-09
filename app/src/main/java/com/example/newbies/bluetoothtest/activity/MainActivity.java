package com.example.newbies.bluetoothtest.activity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.newbies.bluetoothtest.R;
import com.example.newbies.bluetoothtest.util.BluetoothConnectUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author NewBies
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private ListView bluetoothListView;
    private Button setVisibility;
    private Button search;
    private ArrayAdapter<String> devicesListAdapter;
    private BluetoothAdapter bluetoothAdapter;
    /**
     * 打开蓝牙的请求码，注意：这个请求码必须大于0
     */
    private final int REQUEST_ENABLE_BT = 1;
    private ArrayList<String> data = new ArrayList<>();
    /**
     * 创建一个用于接收ACTION_FOUND广播的广播接收器，可以获取到发现设备的相关信息
     */
    private final BroadcastReceiver receiver = new ActionFoundBroadcastReceiver();
    private BluetoothConnectUtil bluetoothConnectUtil;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initBluetooth();
        initView();
        initListener();

    }

    public void initBluetooth(){
        //获取蓝牙适配器实例
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(bluetoothAdapter == null){
            //说明设备不支持蓝牙，你就到此为止了
            Toast.makeText(this,"你的设备不支持蓝牙",Toast.LENGTH_SHORT).show();
            return;
        }

        bluetoothConnectUtil = new BluetoothConnectUtil(this,bluetoothAdapter);
        bluetoothConnectUtil.setUpServerSocket();

        //如果蓝牙未启动
        if(!bluetoothAdapter.isEnabled()){
            //启动蓝牙
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth,REQUEST_ENABLE_BT);
        }

       devicesListAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1);
        //得到已配对蓝牙
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        //如果已经存在配对好了的蓝牙
        if (pairedDevices.size() > 0) {
            //遍历已配对蓝牙
            for (BluetoothDevice device : pairedDevices) {
                devicesListAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        }

        //注册广播接收器
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);
        //注册监听附近设备关闭的广播接收器
        IntentFilter finish = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(receiver, finish);
    }

    protected void initView(){
        setVisibility = (Button)findViewById(R.id.setVisibility);
        search = (Button)findViewById(R.id.search);
        bluetoothListView = (ListView)findViewById(R.id.listView);
        bluetoothListView.setAdapter(devicesListAdapter);

    }

    protected void initListener(){
        setVisibility.setOnClickListener(this);
        search.setOnClickListener(this);
        bluetoothListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //取消扫描，也就是取消监听附件的设备，这样可以很好的降低设备功耗
                bluetoothAdapter.cancelDiscovery();

                //得到设备的Mac地址，一共占据17个字符，且是最后17个字符
                String info = ((TextView)view).getText().toString();
                String address = info.substring(info.length() - 17);

                //通过Mac地址得到BluetoothDevice实例
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
                bluetoothConnectUtil.connect(device,false);
                System.out.println("当前： " + Thread.currentThread().getId());
            }
        });
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.setVisibility:
                bluetoothConnectUtil.write("hello world");
//                //设置设备可检测时间为120秒
//                //PS:如果尚未在设备上启用蓝牙，则启用设备可检测性将会自动启用蓝牙。
//                Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
//                discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120);
//
//                startActivity(discoverableIntent);
                break;
            case R.id.search:
                System.out.println("当前的线程时： " + Thread.currentThread().getId());
//                //如果已经在进行搜索设备，那么就取消搜索
//                if (bluetoothAdapter.isDiscovering()) {
//                    bluetoothAdapter.cancelDiscovery();
//                }
//
//                //请求查找新设备
//                bluetoothAdapter.startDiscovery();
//                break;
            default:break;
        }
    }

    class ActionFoundBroadcastReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // 当查找到蓝牙设备时
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // 从广播中得到一个蓝牙设备
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                //如果该未配对，那么加入到listView适配器中
                if(device.getBondState() != BluetoothDevice.BOND_BONDED){
                    devicesListAdapter.add(device.getName() + "\n" + device.getAddress());
                    devicesListAdapter.notifyDataSetChanged();
                }
            }
//            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
//                Toast.makeText(context, "查询完成", Toast.LENGTH_SHORT).show();
//            }
            //PS:执行设备发现对于蓝牙适配器而言是一个非常繁重的操作过程，并且会消耗大量资源,因此当设备连接过后，应该让其不能被检测到
        }
    }

    /**
     * @param requestCode 就是请求时设置的请求码
     * @param resultCode 处理结果，成功或者失败
     * @param data
     */
    @Override
    public void onActivityResult (int requestCode, int resultCode, Intent data){
        System.err.println(requestCode + "   " + resultCode);
        //开始查找设备
//        bluetoothAdapter.startDiscovery();
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        //不要忘记在活动关闭时取消注册
        unregisterReceiver(receiver);
        bluetoothConnectUtil.cancelConnect();
    }
}
