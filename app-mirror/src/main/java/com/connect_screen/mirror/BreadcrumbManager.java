package com.connect_screen.mirror;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

public class BreadcrumbManager {
    private FragmentManager fragmentManager;
    public static FragmentFactory homeFragmentFactory;

    public BreadcrumbManager(FragmentManager fragmentManager) {
        this.fragmentManager = fragmentManager;
    }

    public void pushBreadcrumb(FragmentFactory fragmentFactory) {
        try {
            // 使用工厂方法创建 Fragment 并替换
            Fragment fragment = fragmentFactory.createFragment();
            fragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .addToBackStack(null)
                    .commit();
        } catch (Throwable e) {
            // ignore
        }
    }

    public void popBreadcrumb() {
        try {
            if (fragmentManager.getFragments().size() > 1) {
                fragmentManager.popBackStack();
            }
        } catch (Exception e) {
            // ignore
        }
    }

    // 添加 FragmentFactory 接口
    public interface FragmentFactory {
        Fragment createFragment();
    }

    // 添加 refreshCurrentFragment 方法
    public void goBackHome() {
        try {
            if (State.currentActivity.get() == null) {
                return;
            }
            while (!fragmentManager.getFragments().isEmpty()) {
                if (!fragmentManager.popBackStackImmediate()) {
                    break;
                }
            }
            pushBreadcrumb(homeFragmentFactory);
        } catch (Exception e) {
            // ignore
        }
    }
}