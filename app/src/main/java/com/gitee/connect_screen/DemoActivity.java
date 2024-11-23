package com.gitee.connect_screen;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class DemoActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo);
        
        TextView textView = findViewById(R.id.demo_text);
        textView.setText("这是在外部显示器上的演示界面");
    }
}