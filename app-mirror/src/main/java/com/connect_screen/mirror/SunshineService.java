package com.connect_screen.mirror;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaCodecInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.connect_screen.mirror.job.SunshineServer;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.List;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

public class SunshineService extends Service {
    public static SunshineService instance;
    private static final String CHANNEL_ID = "SunshineServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final String TAG = "SunshineService";

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 在后台线程中启动 Sunshine 服务器
        String sunshineName = "屏易连-"  + Build.MANUFACTURER + "-" + Build.MODEL;
        SunshineServer.setSunshineName(sunshineName);
        String addr = getWifiIpAddress(this);
        if (addr == null) {
            State.log("无法获取WiFi IP地址");
        } else {
            State.log("发布 moonlight 服务名："  + sunshineName);
            State.log("发布 moonlight ip："  + addr);
        }
        probeH265();

        // 将网络初始化操作移到后台线程
        new Thread(() -> {
            try {
                SunshineServer.setFileStatePath(SunshineService.this.getFilesDir().getAbsolutePath() + "/sunshine_state.json");
                writeCertAndKey(SunshineService.this);
                new Thread(() -> { SunshineServer.start(); }).start();

                if(addr != null) {
                    JmDNS jmdns = JmDNS.create(Inet4Address.getByName(addr));
                    ServiceInfo serviceInfo = ServiceInfo.create(
                            "_nvstream._tcp.local.",
                            "ConnectScreen",
                            47989,
                            "ConnectScreen"
                    );

                    jmdns.registerService(serviceInfo);
                    Log.i("MirrorHomeFragment", "JmDNS服务注册成功");
                }
            } catch (Exception e) {
                Log.e("MirrorHomeFragment", "初始化网络服务失败", e);
            }
        }).start();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Sunshine Service Channel",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("屏易连")
            .setContentText("Sunshine 服务正在运行")
            .setSmallIcon(R.mipmap.ic_mirror)
            .build();
    }

    private boolean probeH265() {
        try {
            // 检查设备是否支持 H.265/HEVC 编码
            android.media.MediaCodecList codecList = new android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS);
            for (android.media.MediaCodecInfo codecInfo : codecList.getCodecInfos()) {
                if (!codecInfo.isHardwareAccelerated()) {
                    continue;
                }
                if (!codecInfo.isEncoder()) {
                    continue;
                }
                if (!isSupported(codecInfo, "video/hevc")) {
                    continue;
                }
                SunshineServer.enableH265();
                return true;
            }
            State.log("设备不支持 H.265/HEVC 编码");
            return false;
        } catch (Exception e) {
            State.log("检查 H.265 编码支持时出错: " + e.getMessage());
            return false;
        }
    }

    private boolean isSupported(MediaCodecInfo codecInfo, String mime) {
        String[] types = codecInfo.getSupportedTypes();
        for (String type : types) {
            if (type.equalsIgnoreCase(mime)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWifiApEnabled(WifiManager mWifiManager) {
        boolean apState = false;
        try {
            // @RequiresPermission(android.Manifest.permission.ACCESS_WIFI_STATE)
            apState = (boolean) mWifiManager.getClass().getMethod("isWifiApEnabled").invoke(mWifiManager);
            Log.i(TAG, "isWifiApEnabled :" + apState + "");
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            Log.e(TAG, "failed to get  isWifiApEnabled", e );
        }
        return apState;
    }

    private static String getApIP() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()){
                NetworkInterface ni = networkInterfaces.nextElement();
                Log.d(TAG, "network interface: " + ni.getName());
                if(ni.isUp() && !ni.isPointToPoint() && !ni.isLoopback() && ("ap0".equals(ni.getName()) || "softap0".equals(ni.getName()) || "wlan2".equals(ni.getName()))){
                    List<InterfaceAddress> interfaceAddresses = ni.getInterfaceAddresses();
                    for (InterfaceAddress interfaceAddress : interfaceAddresses) {
                        if(interfaceAddress.getAddress() != null){
                            Log.d(TAG,"address:"+interfaceAddress.getAddress().toString());
                            if(interfaceAddress.getAddress().toString().contains("/192.168")){
                                String softApIP = interfaceAddress.getAddress().toString().substring(1);
                                Log.d(TAG,"getApIP:"+softApIP);
                                return softApIP;
                            }
                        }
                    }
                }
            }
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public static String getWifiIpAddress(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null || !wifiManager.isWifiEnabled()) {
            return null;
        }
        if (isWifiApEnabled(wifiManager)) {
            String apIp = getApIP();
            if (apIp != null) {
                return apIp;
            }
        }

        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
        // Convert little-endian to big-endian if needed
        byte[] bytes = new byte[4];
        bytes[0] = (byte) (ipAddress & 0xFF);
        bytes[1] = (byte) ((ipAddress >> 8) & 0xFF);
        bytes[2] = (byte) ((ipAddress >> 16) & 0xFF);
        bytes[3] = (byte) ((ipAddress >> 24) & 0xFF);

        try {
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }


    public static void writeCertAndKey(Context context) {
        try {
            // 写入证书文件
            try (InputStream certInput = context.getAssets().open("cacert.pem");
                 FileOutputStream certOutput = context.openFileOutput("cacert.pem", Context.MODE_PRIVATE)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = certInput.read(buffer)) > 0) {
                    certOutput.write(buffer, 0, length);
                }
                SunshineServer.setCertPath(context.getFilesDir().getAbsolutePath() + "/cacert.pem");
            }

            // 写入密钥文件
            try (InputStream keyInput = context.getAssets().open("cakey.pem");
                 FileOutputStream keyOutput = context.openFileOutput("cakey.pem", Context.MODE_PRIVATE)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = keyInput.read(buffer)) > 0) {
                    keyOutput.write(buffer, 0, length);
                }
                SunshineServer.setPkeyPath(context.getFilesDir().getAbsolutePath() + "/cakey.pem");
            }

            android.util.Log.i(TAG, "证书和密钥文件写入成功: " + context.getFilesDir().getAbsolutePath());
        } catch (IOException e) {
            android.util.Log.e("TAG", "写入证书文件失败", e);
        }
    }
} 