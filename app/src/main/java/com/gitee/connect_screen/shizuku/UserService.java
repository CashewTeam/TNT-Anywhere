package com.gitee.connect_screen.shizuku;

import android.util.Log;

import android.os.RemoteException;

public class UserService extends IUserService.Stub  {
    public UserService() {
        Log.i("UserService", "constructor");
    }
    
    /**
     * Reserved destroy method
     */
    @Override
    public void destroy() {
        Log.i("UserService", "destroy");
        System.exit(0);
    }

    @Override
    public void exit() {
        destroy();
    }

    @Override
    public String fetchLogs() throws RemoteException  {
        return "hello world";
    }
}
