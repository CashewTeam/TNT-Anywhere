package com.connect_screen.extend;

import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Display;
import android.widget.TextView;
import android.widget.Button;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class DisplayListFragment extends Fragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_display_list, container, false);

        // 获取Display信息
        DisplayManager displayManager = (DisplayManager) getContext().getSystemService(Context.DISPLAY_SERVICE);
        // 获取所有显示器
        Display[] displays = displayManager.getDisplays();

        // 设置RecyclerView
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        List<Display> displayList = new ArrayList<>();
        for (Display display : displays) {
            if (display.getDisplayId() != State.bridgeDisplayId && display.getDisplayId() != State.mirrorDisplayId) {
                displayList.add(display);
            }
        }
        recyclerView.setAdapter(new DisplayAdapter(displayList, this::onDisplayItemClick));

        // 添加按钮点击事件
        view.findViewById(R.id.btnOpenCast).setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setAction(android.provider.Settings.ACTION_CAST_SETTINGS);
            startActivity(intent);
        });

        return view;
    }

    private void onDisplayItemClick(Display display) {
        State.breadcrumbManager.pushBreadcrumb("屏幕 " + display.getDisplayId(), () -> DisplayDetailFragment.newInstance(display.getDisplayId()));
    }

    // 修改适配器类
    private static class DisplayAdapter extends RecyclerView.Adapter<DisplayAdapter.ViewHolder> {
        private final List<Display> displayList;
        private final OnDisplayClickListener clickListener;

        // 新增接口
        interface OnDisplayClickListener {
            void onDisplayClick(Display display);
        }

        // 修改构造函数
        public DisplayAdapter(List<Display> displayList, OnDisplayClickListener listener) {
            this.displayList = displayList;
            this.clickListener = listener;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_display, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Display display = displayList.get(position);
            String displayInfo = String.format("显示器 ID: %d\n分辨率: %dx%d",
                display.getDisplayId(),
                display.getWidth(),
                display.getHeight());
            holder.displayId.setText(displayInfo);
            holder.displayName.setText("显示器名称: " + display.getName());

            // 设置整个项目的点击事件
            holder.itemView.setOnClickListener(v -> clickListener.onDisplayClick(display));
            // 设置按钮的点击事件
            holder.btnViewDetail.setOnClickListener(v -> clickListener.onDisplayClick(display));
        }

        @Override
        public int getItemCount() {
            return displayList.size();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            public final TextView displayId;
            public final TextView displayName;
            public final Button btnViewDetail;  // 添加按钮引用

            public ViewHolder(View view) {
                super(view);
                displayId = view.findViewById(R.id.display_id);
                displayName = view.findViewById(R.id.display_name);
                btnViewDetail = view.findViewById(R.id.btn_view_detail);  // 初始化按钮
            }
        }
    }
}