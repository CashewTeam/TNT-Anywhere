package com.connect_screen.extend;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RadioGroup;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.graphics.Bitmap;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import android.graphics.Color;
import android.widget.ImageView;
import android.content.ContentValues;
import android.content.ContentResolver;
import android.provider.MediaStore;

public class ShizukuFragment extends Fragment {
    private HttpServer server;
    private static final int PORT = 8888;
    private String serverUrl;
    private String lanIp;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_shizuku, container, false);

        lanIp = getLocalIpAddress();
        serverUrl = ("https://" + lanIp + ":" + PORT);
        startHttpServer();
        
        TextView descriptionText = view.findViewById(R.id.shizukuDescription);
        TextView wiredDesc = view.findViewById(R.id.wiredDescription);
        TextView wirelessDesc = view.findViewById(R.id.wirelessDescription);
        TextView serverUrlText = view.findViewById(R.id.serverUrl);
        ImageView qrCodeImage = view.findViewById(R.id.qrCodeImage);
        Button installButton = view.findViewById(R.id.installButton);
        RadioGroup activationGroup = view.findViewById(R.id.activationGroup);

        descriptionText.setText("安卓屏连的基础功能不需要 Shizuku 授权也可以使用。基础功能包括 USB3.0直连屏幕的单应用投屏，以及安卓自带的无线投屏的单应用投屏。Shizuku 是一个帮助应用获取 adb 权限的工具。Shizuku 不是 ROOT，不需要手机刷机。安卓屏连的竖屏旋转，以及绑定外设到指定显示器等功能需要获得 adb 权限才能工作。虚拟触控板，和悬浮返回键用无障碍权限也能工作，但是有 adb 权限之后会工作得更稳定。");
        
        wiredDesc.setText("请将手机通过 USB 数据线连接到电脑（Windows，Mac等均可以），然后在电脑上打开下面这个网页地址。因为局域网地址的 https 证书是自己签发的，打开的时候会有安全警告，需要手工强制访问才能打开。");
        wirelessDesc.setText("安装 shizuku 应用，并按照 shizuku 应用内的提示启用无线调试激活 shizuku 服务。");

        activationGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.wiredActivation) {
                wiredDesc.setVisibility(View.VISIBLE);
                serverUrlText.setVisibility(View.VISIBLE);
                qrCodeImage.setVisibility(View.VISIBLE);
                wirelessDesc.setVisibility(View.GONE);
                installButton.setVisibility(View.GONE);
            } else {
                wiredDesc.setVisibility(View.GONE);
                serverUrlText.setVisibility(View.GONE);
                qrCodeImage.setVisibility(View.GONE);
                wirelessDesc.setVisibility(View.VISIBLE);
                installButton.setVisibility(View.VISIBLE);
            }
        });

        installButton.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveApkFile();
            } else {
                // 检查并请求存储权限
                if (requireActivity().checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(
                        new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        1001
                    );
                } else {
                    saveApkFile();
                }
            }
        });

        serverUrlText.setText(serverUrl);
        serverUrlText.setPaintFlags(serverUrlText.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        serverUrlText.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
        serverUrlText.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("url", serverUrl);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(requireContext(), "链接已经复制到剪贴板。请在电脑上打开该链接。",
                    Toast.LENGTH_LONG).show();
        });

        generateQRCode(serverUrl, qrCodeImage);

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopHttpServer();
    }

    private void startHttpServer() {
        try {
            server = new HttpServer(lanIp, PORT, requireContext().getAssets());
            server.start();
            State.log("HTTP Server started on: " + serverUrl);
        } catch (IOException e) {
            String errorMsg = "HTTP服务器启动失败: " + e.getMessage();
            Toast.makeText(requireContext(), errorMsg, 
                Toast.LENGTH_SHORT).show();
            State.log(errorMsg);
        }
    }

    private void stopHttpServer() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    private static String getLocalIpAddress() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                    for (InetAddress inetAddress : Collections.list(networkInterface.getInetAddresses())) {
                        if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                            return inetAddress.getHostAddress();
                        }
                    }
                }
            } else {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    NetworkInterface networkInterface = interfaces.nextElement();
                    Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress address = addresses.nextElement();
                        if (!address.isLoopbackAddress() && address instanceof Inet4Address) {
                            return address.getHostAddress();
                        }
                    }
                }
            }
        } catch (Throwable e) {
            return "localhost";  // 如果获取失败则返回 localhost
        }
        return "localhost";  // 如果获取失败则返回 localhost
    }

    private void generateQRCode(String text, ImageView imageView) {
        try {
            BitMatrix bitMatrix = new MultiFormatWriter().encode(
                text, 
                BarcodeFormat.QR_CODE, 
                512, 512
            );
            
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            
            imageView.setImageBitmap(bitmap);
        } catch (Exception e) {
            String errorMsg = "生成二维码失败: " + e.getMessage();
            Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show();
            State.log(errorMsg);
        }
    }

    private void saveApkFile() {
        try {
            String fileName = "moe.shizuku.privileged.api_1049.apk";
            File outputFile;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, "shizuku.apk");
                values.put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive");
                
                ContentResolver resolver = requireContext().getContentResolver();
                Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                
                if (uri != null) {
                    try (InputStream in = requireContext().getAssets().open(fileName);
                         OutputStream out = resolver.openOutputStream(uri)) {
                        copyStream(in, out);
                    }
                }
            } else {
                File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                outputFile = new File(downloadDir, "shizuku.apk");
                
                try (InputStream in = requireContext().getAssets().open(fileName);
                     OutputStream out = new FileOutputStream(outputFile)) {
                    copyStream(in, out);
                }
            }
            
            Toast.makeText(requireContext(), "APK已保存到手机的下载目录，文件名是 shizuku.apk", 
                Toast.LENGTH_LONG).show();
            State.log("APK已保存到手机的下载目录，文件名是 shizuku.apk。请手工前往文件管理器安装。");
            
        } catch (Throwable e) {
            String errorMsg = "保存失败: " + e.getMessage();
            Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show();
            State.log(errorMsg);
        }
    }

    private void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveApkFile();
            } else {
                Toast.makeText(requireContext(), "需要存储权限才能保存APK文件", Toast.LENGTH_SHORT).show();
            }
        }
    }
}