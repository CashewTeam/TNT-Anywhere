package com.gitee.connect_screen.job;

import android.os.Parcel;
import android.os.Parcelable;

public class VirtualDisplayArgs implements Parcelable {
    public final int monitorWidth;
    public final int monitorHeight;
    public final int virtualDisplayWidth;
    public final int refreshRate;
    public final int dpi;
    public final boolean rotatesWithContent;
    public final String virtualDisplayName;

    public VirtualDisplayArgs() {
        this("DisplayLink", 1920, 1080, 1920, 60, 160, false);
    }
    public VirtualDisplayArgs(String virtualDisplayName, int monitorWidth, int monitorHeight, int virtualDisplayWidth, int refreshRate, int dpi, boolean rotatesWithContent) {
        this.virtualDisplayName = virtualDisplayName;
        if(monitorWidth == 0) {
            monitorWidth = 1920;
        }
        if (virtualDisplayWidth == 0) {
            virtualDisplayWidth = 1920;
        }
        if (monitorHeight == 0) {
            monitorHeight = 1080;
        }
        if (refreshRate == 0) {
            refreshRate = 60;
        }
        if (dpi == 0) {
            dpi = 160;
        }
        this.monitorWidth = monitorWidth;
        this.monitorHeight = monitorHeight;
        this.virtualDisplayWidth = virtualDisplayWidth;
        this.refreshRate = refreshRate;
        this.dpi = dpi;
        this.rotatesWithContent = rotatesWithContent;
    }

    protected VirtualDisplayArgs(Parcel in) {
        virtualDisplayName = in.readString();
        monitorWidth = in.readInt();
        monitorHeight = in.readInt();
        virtualDisplayWidth = in.readInt();
        refreshRate = in.readInt();
        dpi = in.readInt();
        rotatesWithContent = in.readByte() != 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(virtualDisplayName);
        dest.writeInt(monitorWidth);
        dest.writeInt(monitorHeight);
        dest.writeInt(virtualDisplayWidth);
        dest.writeInt(refreshRate);
        dest.writeInt(dpi);
        dest.writeByte((byte) (rotatesWithContent ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<VirtualDisplayArgs> CREATOR = new Creator<VirtualDisplayArgs>() {
        @Override
        public VirtualDisplayArgs createFromParcel(Parcel in) {
            return new VirtualDisplayArgs(in);
        }

        @Override
        public VirtualDisplayArgs[] newArray(int size) {
            return new VirtualDisplayArgs[size];
        }
    };
}