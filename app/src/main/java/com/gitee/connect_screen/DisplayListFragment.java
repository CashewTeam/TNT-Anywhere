package com.gitee.connect_screen;

import static android.content.Context.DISPLAY_SERVICE;
import static androidx.core.content.ContextCompat.getSystemService;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Display;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Arrays;
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
        recyclerView.setAdapter(new DisplayAdapter(Arrays.asList(displays), this::onDisplayItemClick));

        return view;
    }

    // 新增：处理显示器项的点击事件
    private void onDisplayItemClick(Display display) {
        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            activity.pushBreadcrumb("屏幕 " + display.getDisplayId(), () -> DisplayDetailFragment.newInstance(display.getDisplayId()));
        }
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

            // 简化的点击监听器设置
            holder.itemView.setOnClickListener(v -> clickListener.onDisplayClick(display));
        }

        @Override
        public int getItemCount() {
            return displayList.size();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            public final TextView displayId;
            public final TextView displayName;

            public ViewHolder(View view) {
                super(view);
                displayId = view.findViewById(R.id.display_id);
                displayName = view.findViewById(R.id.display_name);
            }
        }
    }
}