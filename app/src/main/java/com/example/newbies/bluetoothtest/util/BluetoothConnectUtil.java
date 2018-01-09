package com.example.newbies.bluetoothtest.util;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 *
 * @author NewBies
 * @date 2018/1/7
 */
public class BluetoothConnectUtil {

    /**
     * 初始状态
     */
    public static final int STATE_NONE = 0;
    /**
     * 准备连接状态，针对于该设备为服务端
     */
    public static final int STATE_READY_TO_CONNECT = 1;
    /**
     * 正在连接状态
     */
    public static final int STATE_CONNECTING = 2;
    /**
     * 已连接状态
     */
    public static final int STATE_CONNECTED = 3;
    /**
     * 蓝牙连接状态变量
     */
    private int connectState;
    /**
     * 上下文对象
     */
    private Context context;
    /**
     * 蓝牙适配器实例
     */
    private BluetoothAdapter bluetoothAdapter;
    /**
     * 此应用程序唯一的UUID
     */
    private static final UUID MY_UUID_SECURE = UUID.fromString("aa87c0d0-afac-11de-8a39-0800200c9a66");
    private static final UUID MY_UUID_INSECURE = UUID.fromString("ace255c0-200a-11e0-ac64-0800200c9a66");
    /**
     * 需要配对连接的服务器线程
     */
    private AcceptThread secureAcceptThread;
    /**
     * 不需要配对连接的服务器线程
     */
    private AcceptThread insecureAcceptThread;
    /**
     * 客户端建立连接的线程
     */
    private ConnectThread connectThread;
    /**
     * 进行数据传输的线程
     */
    private DataTransmissionThread dataTransmissionThread;

    public BluetoothConnectUtil(Context context, BluetoothAdapter bluetoothAdapter){
        this.context = context;
        this.bluetoothAdapter = bluetoothAdapter;
    }

    /**
     * 建立服务端，该方法应该为同步方法，因为该方法在建立连接线程中连接失败时会被调用，在传输数据线程中连接丢失时也会被访问，在UI线程中第一次建立连接也会被访问
     */
    public synchronized void setUpServerSocket(){
        if(connectThread != null){
            connectThread.cancel();
            connectThread = null;
        }

        if(dataTransmissionThread != null){
            dataTransmissionThread.cancel();
            dataTransmissionThread = null;
        }

        if(secureAcceptThread == null){
            secureAcceptThread = new AcceptThread(true);
            secureAcceptThread.start();
        }
        if(insecureAcceptThread == null){
            insecureAcceptThread = new AcceptThread(false);
            insecureAcceptThread.start();
        }

    }

    /**
     * 建立连接，这里也应该为同步方法，该方法是在点击事件中执行，点击事件应该是异步的，也就是说，多次点击，则会多次对该方法调用
     */
    public synchronized void connect(BluetoothDevice device, boolean secure){
        //取消正在进行连接的线程
        if(connectState == STATE_CONNECTING){
            if(connectThread != null){
                connectThread.cancel();
                connectThread = null;
            }
        }
        //取消正在进行通信的线程
       if(connectState == STATE_CONNECTED){
            if(dataTransmissionThread != null){
                dataTransmissionThread.cancel();
                dataTransmissionThread = null;
            }
       }
       //启动用于连接的线程
       connectThread = new ConnectThread(device, secure);
       connectThread.start();
       Toast.makeText(context, "正在连接", Toast.LENGTH_SHORT).show();
    }

    /**
     * 开启进行数据传输的线程，该方法应该为同步方法，因为该方法会在服务器建立时会调用。同时在接收连接时也会被调用，
     * 如果该设备作为客户端去连接服务端，那么该方法被调用，但是如果此时有其他设备来连接该设备且连接成功，那么该设备
     * 又将会作为服务端调用此方法，这里就存在线程对资源的抢占了
     */
    public synchronized void setUpTransferDataThread(BluetoothSocket socket){
        //取消进行连接的线程
        if(connectThread != null){
            connectThread.cancel();
            connectThread = null;
        }
        //取消进行数据传输的线程
        if(dataTransmissionThread != null){
            dataTransmissionThread.cancel();
            dataTransmissionThread = null;
        }
        //取消服务端线程
        if(secureAcceptThread != null){
            secureAcceptThread.cancel();
            secureAcceptThread = null;
        }
        if(insecureAcceptThread != null){
            insecureAcceptThread.cancel();
            insecureAcceptThread = null;
        }

        dataTransmissionThread = new DataTransmissionThread(socket);
        dataTransmissionThread.start();
    }

    /**
     * 当活动被摧毁时取消所有线程
     */
    public void cancelConnect(){
        //取消连接时，取消相关线程，状态还原
        Toast.makeText(context, "取消连接", Toast.LENGTH_SHORT).show();
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        if (dataTransmissionThread != null) {
            dataTransmissionThread.cancel();
            dataTransmissionThread = null;
        }

        if (secureAcceptThread != null) {
            secureAcceptThread.cancel();
            secureAcceptThread = null;
        }

        if (insecureAcceptThread != null) {
            insecureAcceptThread.cancel();
            insecureAcceptThread = null;
        }
        connectState = STATE_NONE;
    }

    /**
     * 连接失败
     */
    private void connectionFailed(){
        //当连接失败时重启服务端，状态归零
        this.connectState = STATE_NONE;
        this.setUpServerSocket();
    }

    /**
     * 连接丢失
     */
    private void connectionLost(){
        //当连接丢失时重启服务端，状态归零
        this.connectState = STATE_NONE;
        this.setUpServerSocket();
    }

    /**
     * 发送信息
     * @param message
     */
    public void write(String message) {
        byte[] out = message.getBytes();

        if (connectState != STATE_CONNECTED) {
            return;
        }

        dataTransmissionThread.write(out);
    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket serverSocket;

        public AcceptThread(boolean secure) {
            BluetoothServerSocket tempServerSocket = null;
            try {
                if(secure){
                    tempServerSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("蓝牙连接测试1",MY_UUID_SECURE);
                }
                else {
                    tempServerSocket = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("蓝牙连接测试2",MY_UUID_INSECURE);
                }

            } catch (IOException e) { }
            serverSocket = tempServerSocket;
            connectState = STATE_READY_TO_CONNECT;
        }

        @Override
        public void run() {
            BluetoothSocket socket = null;
            // 继续监听，直到发生异常或超时
            //只有当该设备为服务端时才能接受连接请求，比如说，该设备正在连接其他设备时，这是该设备就作为客户端，那么这个时候它就不能接受来自其他客户端的连接
            while (connectState != STATE_CONNECTED) {
                try {
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
                //如果连接被建立
                if (socket != null) {
                    synchronized (this) {
                        switch (connectState) {
                            case STATE_READY_TO_CONNECT:
                            case STATE_CONNECTING:
                                //开启数据通信线程
                                setUpTransferDataThread(socket);
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                // Either not ready or already connected. Terminate new socket.
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                break;
                            default:
                                break;
                        }
                    }

                    try {
                        //释放服务器套接字及其所有资源
                        serverSocket.close();
                    } catch (IOException e) {
                        Toast.makeText(context, "服务器连接出错", Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }
                }
            }
        }

        /**
         * 将取消侦听套接字，并导致线程完成
         */
        public void cancel() {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 此线程用于建立连接
     */
    private class ConnectThread extends Thread{
        private final BluetoothSocket socket;
        private final BluetoothDevice device;

        public ConnectThread(BluetoothDevice device, boolean secure) {
            this.device = device;
            BluetoothSocket tempSocket = null;

            //通过给定的BluetoothDevice获取到用于连接的BluetoothSocket
            try {
                if (secure) {
                    tempSocket = device.createRfcommSocketToServiceRecord(MY_UUID_SECURE);
                } else {
                    tempSocket = device.createInsecureRfcommSocketToServiceRecord(MY_UUID_INSECURE);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            socket = tempSocket;
            //将状态设置为正在连接
            connectState = STATE_CONNECTING;
        }

        @Override
        public void run() {
            //取消搜索
            bluetoothAdapter.cancelDiscovery();

            //建立连接
            try {
                //这是一个阻塞调用，只会返回一个成功的连接或异常
               System.out.println("点击一次"+ Thread.currentThread().getId() );
                socket.connect();
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    socket.close();
                } catch (IOException e2) {
                    e.printStackTrace();
                }
                //建立连接失败
                connectionFailed();
                return;
            }

            synchronized (this){
                connectThread = null;
            }

            //建立数据通信
            setUpTransferDataThread(socket);
        }

        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 此线程在与远程设备连接期间运行。它处理所有传入和传出的传输。
     */
    private class DataTransmissionThread extends Thread {
        private final BluetoothSocket socket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public DataTransmissionThread(BluetoothSocket socket) {
            this.socket = socket;
            InputStream tempInputStream = null;
            OutputStream tempOutputStream = null;

            //得到客户端的输入流和输出流
            try {
                tempInputStream = socket.getInputStream();
                tempOutputStream = socket.getOutputStream();
            } catch (IOException e) {}

            inputStream = tempInputStream;
            outputStream = tempOutputStream;
            connectState = STATE_CONNECTED;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while (connectState == STATE_CONNECTED) {
                try {
                    //从输入流中读取数据，接收数据时使用了线程
                    bytes = inputStream.read(buffer);
                    System.err.println(bytes);
                } catch (IOException e) {
                    e.printStackTrace();
                    //连接丢失
                    connectionLost();
                    break;
                }
            }
        }

        /**
         * 传输数据时不存在线程
         * @param buffer
         */
        public void write(byte[] buffer) {
            try {
                outputStream.write(buffer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
