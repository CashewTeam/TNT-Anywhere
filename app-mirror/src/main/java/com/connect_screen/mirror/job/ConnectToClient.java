package com.connect_screen.mirror.job;

import com.connect_screen.mirror.Pref;
import com.connect_screen.mirror.State;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class ConnectToClient {
    public static void connect() {
        String clientIpAndPort = Pref.getSelectedClient();
        // 解析客户端 IP 和端口
        String[] parts = clientIpAndPort.split(":");
        String clientIp = parts[0];
        int clientPort = -1;
        try {
            clientPort = Integer.parseInt(parts[1]);
        } catch(Exception e) {
            State.log("客户端地址非法: " + clientIpAndPort + "  " + e);
            return;
        }
        // 获取服务器 IP（与客户端同网段）
        String serverIp = findServerIpInSameSubnet(clientIp);
        if (serverIp == null) {
            State.log("找不到和客户端在同网段的ip");
            return;
        }
        String request = "{\"action\": \"connect\", \"ip\": \"" + serverIp + "\", \"pin\": \"1234\"}";
        State.log("连接请求: " + request);
    }
    
    /**
     * 查找与客户端 IP 同网段的本地 IP 地址
     * @param clientIp 客户端 IP 地址
     * @return 同网段的本地 IP 地址，如果未找到则返回 null
     */
    private static String findServerIpInSameSubnet(String clientIp) {
        try {
            // 获取客户端 IP 的网段前缀
            String clientSubnet = getSubnetPrefix(clientIp);
            
            // 遍历所有网络接口
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                
                // 跳过禁用的接口
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }
                
                // 检查每个接口的 IP 地址
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    String localIp = address.getHostAddress();
                    
                    // 跳过 IPv6 地址
                    if (localIp.contains(":")) {
                        continue;
                    }
                    
                    // 检查是否与客户端在同一网段
                    if (getSubnetPrefix(localIp).equals(clientSubnet)) {
                        return localIp;
                    }
                }
            }
        } catch (SocketException e) {
            State.log("查找匹配的ip失败: " + e);
        }
        
        return null;
    }
    
    /**
     * 获取 IP 地址的网段前缀（假设是 C 类网络，即前 24 位）
     * @param ip IP 地址
     * @return 网段前缀
     */
    private static String getSubnetPrefix(String ip) {
        // 简单实现：假设是 C 类网络，取前三个数字
        String[] octets = ip.split("\\.");
        if (octets.length >= 3) {
            return octets[0] + "." + octets[1] + "." + octets[2];
        }
        return "";
    }
}
