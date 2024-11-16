package com.gitee.connect_screen;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import java.util.ArrayList;
import java.util.List;

public class BreadcrumbManager {
    private LinearLayout breadcrumb;
    private List<String> navigationPath = new ArrayList<>();
    private FragmentManager fragmentManager;

    public BreadcrumbManager(Context context, FragmentManager fragmentManager, LinearLayout breadcrumb) {
        this.breadcrumb = breadcrumb;
        this.fragmentManager = fragmentManager;
    }

    public void pushBreadcrumb(String newPath, Fragment fragment) {
        if (!newPath.isEmpty() && !navigationPath.contains(newPath)) {
            navigationPath.add(newPath);
        }
        updateBreadcrumbView();
        // 替换 Fragment
        fragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit();
    }

    public void popBreadcrumb() {
        if (navigationPath.size() > 1) {
            navigationPath.remove(navigationPath.size() - 1);
        } else {
            ((MainActivity) fragmentManager.findFragmentById(R.id.fragmentContainer).getActivity()).finish();
        }
        updateBreadcrumbView();
        // 回退 Fragment
        fragmentManager.popBackStack();
    }

    private void updateBreadcrumbView() {
        breadcrumb.removeAllViews();

        for (int i = 0; i < navigationPath.size(); i++) {
            TextView separator = new TextView(breadcrumb.getContext());
            separator.setText(" > ");
            breadcrumb.addView(separator);

            TextView pathView = new TextView(breadcrumb.getContext());
            pathView.setText(navigationPath.get(i));
            pathView.setTextColor(breadcrumb.getContext().getResources().getColor(R.color.blue));
            final int index = i;
            pathView.setClickable(true);
            pathView.setOnClickListener(v -> {
                // 清空导航路径直到点击的路径项
                while (navigationPath.size() > index + 1) {
                    popBreadcrumb();
                }
            });
            breadcrumb.addView(pathView);
        }
    }

    public LinearLayout getBreadcrumbView() {
        return breadcrumb;
    }
}