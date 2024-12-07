package com.gitee.connect_screen.touch;

import java.util.ArrayList;
import java.util.List;

public class TouchData {

    public static class TouchPoint {
        public boolean isValid;
        public int contactId;
        public int x;
        public int y;
        public boolean isTouched;
    }

    public int reportId;
    public List<TouchPoint> points;

    public TouchData() {
        points = new ArrayList<>();
    }
}
