package com.easycast.source;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class MainPagerAdapter extends FragmentStateAdapter {
    public MainPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 1) {
            return new TntSettingsFragment();
        }
        if (position == 2) {
            return new StreamingSettingsFragment();
        }
        return new HomePageFragment();
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}
