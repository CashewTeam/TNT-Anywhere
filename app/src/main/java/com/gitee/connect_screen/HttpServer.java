package com.gitee.connect_screen;

import fi.iki.elonen.NanoHTTPD;
import android.content.res.AssetManager;
import java.io.IOException;
import java.io.InputStream;

public class HttpServer extends NanoHTTPD {
    private final AssetManager assetManager;

    public HttpServer(int port, AssetManager assetManager) {
        super(port);
        this.assetManager = assetManager;
    }

    @Override
    public Response serve(IHTTPSession session) {
        State.log("收到HTTP请求 - Method: " + session.getMethod() + 
                 ", URI: " + session.getUri() + 
                 ", Remote IP: " + session.getRemoteIpAddress());
        
        String uri = session.getUri();
        uri = uri.equals("/") ? "/index.html" : uri;
        try {
            InputStream input = assetManager.open(uri.substring(1));
            String mimeType = getMimeType(uri);
            
            return newChunkedResponse(Response.Status.OK, mimeType, input);
        } catch (IOException e) {
            State.log("failed to get " + uri + ": " + e.getMessage());
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found");
        }
    }

    private String getMimeType(String uri) {
        if (uri.endsWith(".html")) return "text/html";
        if (uri.endsWith(".apk")) return "application/vnd.android.package-archive";
        if (uri.endsWith(".css")) return "text/css";
        if (uri.endsWith(".js")) return "application/javascript";
        return "application/octet-stream";
    }
} 