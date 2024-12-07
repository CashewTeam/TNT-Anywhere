package com.gitee.connect_screen.touch;

import java.util.ArrayList;
import java.util.List;

public class TouchParsers {
    // 1. 基础格式 (常见于简单触摸屏)
    public static TouchData parseWithBasicFormat(byte[] data, int length) {
        // 基础触摸数据格式结构（每个触摸点5字节）:
        // 字节0: 触摸点数量 (最大10点)
        // 对于每个触摸点:
        // 字节1: [7:4]位 = 触摸点ID (0-15)
        //       [3:0]位 = 触摸状态 (1=按下, 0=释放)
        // 字节2-3: X坐标 (16位小端序)
        // 字节4-5: Y坐标 (16位小端序)

        TouchData result = new TouchData();
        // 确保至少有一个完整的数据包（1字节计数 + 至少一个触摸点的5字节数据）
        if (length < 5) return result;

        // 获取触摸点数量（第一个字节）
        int touchCount = data[0] & 0xFF;
        // 限制最大触摸点数为10，防止缓冲区溢出
        touchCount = Math.min(touchCount, 10);

        // 从第二个字节开始解析触摸点数据
        int offset = 1;
        for (int i = 0; i < touchCount && offset + 5 <= length; i++) {
            TouchData.TouchPoint point = new TouchData.TouchPoint();

            // 解析触摸信息字节
            int touchInfo = data[offset] & 0xFF;
            // 获取触摸点ID（高4位）
            point.contactId = (touchInfo >> 4) & 0x0F;
            // 获取触摸状态（低4位，1表示按下）
            point.isTouched = (touchInfo & 0x0F) == 1;

            // 解析X坐标（小端序，低字节在前）
            // 字节2 = X坐标低8位
            // 字节3 = X坐标高8位
            point.x = ((data[offset + 2] & 0xFF) << 8) | (data[offset + 1] & 0xFF);

            // 解析Y坐标（小端序，低字节在前）
            // 字节4 = Y坐标低8位
            // 字节5 = Y坐标高8位
            point.y = ((data[offset + 4] & 0xFF) << 8) | (data[offset + 3] & 0xFF);

            // 当X或Y坐标不为0时认为该触摸点有效
            point.isValid = (point.x != 0 || point.y != 0);

            // 添加解析后的触摸点到结果列表
            result.points.add(point);
            // 移动偏移量到下一个触摸点数据（每个触摸点5字节）
            offset += 5;
        }
        return result;
    }

    // 2. 扩展格式 (常见于高端触摸屏)
    public static TouchData parseWithExtendedFormat(byte[] data, int length) {
        // 扩展格式 (每触摸点8字节):
        // byte 0: 报告ID
        // byte 1: 触摸点数量
        // 每个触摸点:
        // byte 0: 触摸点ID
        // byte 1: 状态字节 (包含压力、悬停等信息)
        // byte 2-5: X坐标 (32位)
        // byte 6-9: Y坐标 (32位)
        TouchData result = new TouchData();
        if (length < 8) return result;

        // 扩展格式通常包含更多信息
        result.reportId = data[0] & 0xFF;
        int touchCount = data[1] & 0xFF;
        touchCount = Math.min(touchCount, 10);

        int offset = 2;
        for (int i = 0; i < touchCount && offset + 8 <= length; i++) {
            TouchData.TouchPoint point = new TouchData.TouchPoint();

            point.contactId = data[offset] & 0xFF;
            point.isTouched = (data[offset + 1] & 0x01) != 0;

            // 32位坐标值处理
            point.x = ((data[offset + 4] & 0xFF) << 24) |
                    ((data[offset + 3] & 0xFF) << 16) |
                    ((data[offset + 2] & 0xFF) << 8) |
                    (data[offset + 1] & 0xFF);

            point.y = ((data[offset + 8] & 0xFF) << 24) |
                    ((data[offset + 7] & 0xFF) << 16) |
                    ((data[offset + 6] & 0xFF) << 8) |
                    (data[offset + 5] & 0xFF);

            point.isValid = (point.x != 0 || point.y != 0);
            result.points.add(point);
            offset += 8;
        }
        return result;
    }

    // 3. Wacom格式 (数位板常用)
    public static TouchData parseWacomFormat(byte[] data, int length) {
        // Wacom格式:
        // byte 0: 报告ID
        // byte 1: [7:6]触摸点数, [5:0]扫描时间
        // 每个触摸点:
        // byte 0: [7]接触标志, [6:0]触摸ID
        // byte 1-2: X坐标 (16位)
        // byte 3-4: Y坐标 (16位)
        // byte 5: 压力值
        // ...
        throw new RuntimeException("Wacom格式解析未实现");
    }

    // 4. FocalTech格式
    public static TouchData parseFocalTechFormat(byte[] data, int length) {
        // FocalTech格式:
        // byte 0: 手势ID
        // byte 1: 触摸点数量
        // 每个触摸点:
        // byte 0: [7:4]触摸事件, [3:0]触摸ID
        // byte 1-2: X坐标
        // byte 3-4: Y坐标
        // byte 5: 压力值
        // ...
        throw new RuntimeException("FocalTech格式解析未实现");
    }

    // 5. Goodix格式
    public static TouchData parseGoodixFormat(byte[] data, int length) {
        // Goodix格式:
        // byte 0: 0xBA (固定标识)
        // byte 1: 触摸点数量
        // byte 2: 帧计数器
        // 每个触摸点:
        // byte 0: 触摸ID
        // byte 1-2: X坐标 (12位)
        // byte 3-4: Y坐标 (12位)
        // byte 5: 触摸尺寸/压力
        // ...
        throw new RuntimeException("Goodix格式解析未实现");
    }

    // 6. Microsoft Precision Touchpad格式
    public static TouchData parsePTPFormat(byte[] data, int length) {
        // Microsoft PTP格式:
        // byte 0: 报告ID (0x5)
        // byte 1: [7:4]触摸点数, [3:0]按钮状态
        // byte 2: 扫描时间
        // 每个触摸点:
        // byte 0-1: 触摸点ID和状态
        // byte 2-3: X坐标 (0-32767)
        // byte 4-5: Y坐标 (0-32767)
        // byte 6-7: 接触宽度和高度
        // ...
        throw new RuntimeException("Microsoft Precision Touchpad格式解析未实现");
    }

    // 7. EGalaxEETI格式
    public static TouchData parseEGalaxEETIFormat(byte[] data, int length) {
        TouchData touchData = new TouchData();

        // 数据格式说明（每个触摸点8字节）:
        // byte 0: 保留字节
        // byte 1: 触摸状态 (0=未触摸，非0=触摸)
        // byte 2: 保留字节
        // byte 3-4: X坐标 (小端序，低字节在前)
        // byte 5-6: Y坐标 (小端序，低字节在前)
        // byte 7: 保留字节

        // 根据总数据长度计算触摸点数量
        int pointCount = length / 8;
        // 限制最大触摸点数为10，防止内存溢出
        pointCount = Math.min(pointCount, 10);

        for (int i = 0; i < pointCount; i++) {
            // 计算当前触摸点数据的起始字节位置
            int offset = i * 8;

            TouchData.TouchPoint point = new TouchData.TouchPoint();
            // 使用索引作为触摸点ID
            point.contactId = i;

            // 解析X坐标（小端序）
            // data[offset + 3] = X坐标高字节
            // data[offset + 4] = X坐标低字节
            point.x = ((data[offset + 4] & 0xFF) << 8) | (data[offset + 3] & 0xFF);

            // 解析Y坐标（小端序）
            // data[offset + 5] = Y坐标高字节
            // data[offset + 6] = Y坐标低字节
            point.y = ((data[offset + 6] & 0xFF) << 8) | (data[offset + 5] & 0xFF);

            // 检查触摸状态（第2个字节）
            point.isTouched = (data[offset + 1] != 0);

            // 当坐标值大于0时认为该点有效
            point.isValid = point.x > 0 && point.y > 0;

            touchData.points.add(point);
        }

        return touchData;
    }

    public static TouchData parseWithHidFormat(byte[] data, int length, TouchInputFormat inputFormat) {
        TouchData result = new TouchData();
        if (inputFormat == null || length < 3) return result;

        int currentBit = 0;
        List<TouchData.TouchPoint> points = new ArrayList<>();
        TouchData.TouchPoint currentPoint = null;

        // 基于HID报告描述符解析
        for (TouchInputFormat.FieldInfo field : inputFormat.fields) {
            long value = extractBits(data, currentBit, field.size * field.count);

            if (field.usagePage == 0x0D) { // Digitizer
                switch (field.usage) {
                    case 0x51: // Contact ID
                        currentPoint = new TouchData.TouchPoint();
                        currentPoint.contactId = (int)value;
                        points.add(currentPoint);
                        break;
                    case 0x42: // Tip Switch
                        if (currentPoint != null) {
                            currentPoint.isTouched = value != 0;
                        }
                        break;
                }
            } else if (field.usagePage == 0x01) { // Generic Desktop
                if (currentPoint != null) {
                    switch (field.usage) {
                        case 0x30: // X
                            currentPoint.x = (int)value;
                            break;
                        case 0x31: // Y
                            currentPoint.y = (int)value;
                            break;
                    }
                }
            }
            currentBit += field.size * field.count;
        }

        result.points = points;
        return result;
    }

    private static long extractBits(byte[] data, int startBit, int length) {
        long result = 0;
        for (int i = 0; i < length; i++) {
            int byteIndex = (startBit + i) / 8;
            int bitIndex = (startBit + i) % 8;
            if (byteIndex >= data.length) break;

            if ((data[byteIndex] & (1 << bitIndex)) != 0) {
                result |= (1L << i);
            }
        }
        return result;
    }
}
