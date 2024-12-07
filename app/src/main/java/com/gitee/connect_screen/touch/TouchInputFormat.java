package com.gitee.connect_screen.touch;

import java.util.ArrayList;
import java.util.List;

public class TouchInputFormat {
    public List<TouchInputFormat.FieldInfo> fields = new ArrayList<>();
    public boolean hasPressure = false;
    public boolean hasInRange = false;
    public boolean hasTipSwitch = false;
    public int totalBits = 0;

    // 添加坐标范围信息
    public int xMin = 0, xMax = 0;
    public int yMin = 0, yMax = 0;

    public void addField(TouchInputFormat.FieldInfo field) {
        fields.add(field);
        totalBits += field.size * field.count;
    }

    public static class FieldInfo {
        public final int size;
        public final int count;
        public int usage = 0;
        public int usagePage = 0;

        public FieldInfo(int size, int count) {
            this.size = size;
            this.count = count;
        }
    }
}
