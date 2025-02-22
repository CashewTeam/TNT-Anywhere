package com.gitee.connect_screen;

import static com.gitee.connect_screen.job.AcquireShizuku.SHIZUKU_PERMISSION_REQUEST_CODE;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.gitee.connect_screen.shizuku.ShizukuUtils;

import rikka.shizuku.Shizuku;

public class AboutFragment extends Fragment {

            
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_about, container, false);
        TextView aboutContent = view.findViewById(R.id.aboutContent);
        aboutContent.setText("本应用使用了DisplayLink®的驱动程序(.so文件)用于支持DisplayLink®设备的连接功能。DisplayLink®是Synaptics Incorporated的注册商标。我们仅将其驱动程序用于实现与DisplayLink®设备的兼容性，未对驱动程序进行任何修改。\n\n" +
                "- DisplayLink®驱动程序的所有权利均属于Synaptics Incorporated\n" +
                "- 本应用仅将DisplayLink®驱动用于其预期用途，即支持DisplayLink®设备的连接\n" +
                "- 用户在使用DisplayLink®相关功能时应遵守Synaptics Incorporated的相关许可条款\n" +
                "- 本应用与Synaptics Incorporated没有任何官方关联，不代表或暗示与Synaptics Incorporated存在任何合作关系\n\n" +
                "如有任何与DisplayLink®相关的法律问题，请直接联系Synaptics Incorporated：www.synaptics.com");

        TextView xiaohongshuLink = view.findViewById(R.id.xiaohongshuLink);
        xiaohongshuLink.setOnClickListener(v -> openUrl("https://www.xiaohongshu.com/user/profile/602cc4c0000000000100be64"));

        TextView bilibiliLink = view.findViewById(R.id.bilibiliLink);
        bilibiliLink.setOnClickListener(v -> openUrl("https://space.bilibili.com/494726825"));

        TextView douyinLink = view.findViewById(R.id.douyinLink);
        douyinLink.setOnClickListener(v -> openUrl("https://www.douyin.com/user/MS4wLjABAAAAolJRQWuFI6KZwaBUvPfzDejygnorK2K-CY_6b1OuWQM"));

        TextView youtubeLink = view.findViewById(R.id.youtubeLink);
        youtubeLink.setOnClickListener(v -> openUrl("https://www.youtube.com/@connect-screen"));

        TextView qqLink = view.findViewById(R.id.qqLink);
        qqLink.setOnClickListener(v -> joinQQGroup());

        TextView websiteLink = view.findViewById(R.id.websiteLink);
        websiteLink.setOnClickListener(v -> openUrl("https://connect-screen.com"));

        TextView versionText = view.findViewById(R.id.versionText);
        try {
            String versionName = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
            String androidVersion = android.os.Build.VERSION.RELEASE;
            versionText.setText("版本：" + versionName + " (Android系统 " + androidVersion + ")");
        } catch (Exception e) {
            versionText.setText("版本：未知");
        }


        View header = view.findViewById(R.id.header);

        return view;
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    public void joinQQGroup() {
        String key = "ngIy53SQRlz6tAO0UEkmALBjvGKkDYrq";
        Intent intent = new Intent();
        intent.setData(Uri.parse("mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26jump_from%3Dwebapi%26k%3D" + key));
        try {
            startActivity(intent);
        } catch (Exception e) {
            openUrl("https://connect-screen.com");
        }
    }

} 