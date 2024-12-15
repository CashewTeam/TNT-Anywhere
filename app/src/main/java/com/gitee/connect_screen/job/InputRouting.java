package com.gitee.connect_screen.job;

import com.gitee.connect_screen.State;
import com.gitee.connect_screen.shizuku.ServiceUtils;

import android.hardware.input.IInputManager;
import android.os.RemoteException;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.InputDevice;

import java.util.HashMap;
import java.util.Map;

public class InputRouting {
    public static Map<String, String> getInputDeviceDescriptorToPortMap() {
        if (State.userService == null) {
            State.log("user service 未启动，无法获取输入设备 descriptor -> port 的映射");
            return new HashMap<>();
        }
        Map<String, String> map = new HashMap<>();
        try {
            String inputDump = State.userService.dumpsysInput();
            String[] lines = inputDump.split("\n");
            String lastDescriptor = "";
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("Descriptor:")) {
                    lastDescriptor = line.substring("Descriptor:".length()).trim();
                }
                if (line.startsWith("Location:")) {
                    String inputPort = line.substring("Location:".length()).trim();
                    map.put(lastDescriptor, inputPort);
                }
            }
        } catch (RemoteException ex) {
            throw new RuntimeException(ex);
        }
        return map;
    }

    public static void bindInputToDisplay(DisplayInfo displayInfo, InputDevice inputDevice, IInputManager inputManager, Map<String, String> inputDeviceDescriptorToPortMap) {
        if (!inputDevice.isExternal()) {
            return;
        }
        try {
            inputManager.removeUniqueIdAssociationByDescriptor(inputDevice.getDescriptor());
            inputManager.addUniqueIdAssociationByDescriptor(inputDevice.getDescriptor(), String.valueOf(displayInfo.uniqueId));
            State.log("成功更新输入设备路由: " + inputDevice);
        } catch(Error e) {
            String inputPort = inputDeviceDescriptorToPortMap.get(inputDevice.getDescriptor());
            if (inputPort == null) {
                State.log("未能更新输入设备路由: " + inputDevice + ", " + e.getMessage());
            } else {
                try {
                    inputManager.removeUniqueIdAssociation(inputPort);
                    inputManager.addUniqueIdAssociation(inputPort, String.valueOf(displayInfo.uniqueId));
                } catch(Error e2) {
                    State.log("改用 input port 仍然未能更新输入设备路由: " + inputDevice + ", " + e.getMessage());
                }
            }
        }
    }
}
