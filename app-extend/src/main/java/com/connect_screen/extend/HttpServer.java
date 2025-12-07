package com.connect_screen.extend;

import fi.iki.elonen.NanoHTTPD;
import android.content.res.AssetManager;
import java.io.IOException;
import java.io.InputStream;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.security.*;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;

import java.math.BigInteger;
import java.util.Date;
import org.bouncycastle.x509.X509V3CertificateGenerator;

public class HttpServer extends NanoHTTPD {
    private final AssetManager assetManager;

    public HttpServer(String ip, int port, AssetManager assetManager) {
        super(port);
        this.assetManager = assetManager;
        try {
            makeSecure(generateSSLSocketFactory(ip), null);
        } catch (Exception e) {
            State.log("SSL证书生成失败: " + e.getMessage());
        }
    }

    private SSLServerSocketFactory generateSSLSocketFactory(String ip) throws Exception {
        // 添加BouncyCastle作为安全提供者
        Security.addProvider(new BouncyCastleProvider());
        
        // 生成密钥对
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keypair = keyGen.generateKeyPair();
        
        // 生成证书
        X509V3CertificateGenerator certGen = new X509V3CertificateGenerator();
        X500Principal dnName = new X500Principal("CN=" + ip);
        
        certGen.setSerialNumber(BigInteger.valueOf(System.currentTimeMillis()));
        certGen.setSubjectDN(dnName);
        certGen.setIssuerDN(dnName);
        certGen.setNotBefore(new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000));
        certGen.setNotAfter(new Date(System.currentTimeMillis() + 365 * 24 * 60 * 60 * 1000L));
        certGen.setPublicKey(keypair.getPublic());
        certGen.setSignatureAlgorithm("SHA256WithRSAEncryption");
        
        X509Certificate cert = certGen.generate(keypair.getPrivate());
        
        // 创建密钥库
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setKeyEntry("alias", keypair.getPrivate(), "password".toCharArray(), new X509Certificate[]{cert});
        
        // 创建SSL上下文
        SSLContext sslContext = SSLContext.getInstance("TLS");
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, "password".toCharArray());
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
        
        return sslContext.getServerSocketFactory();
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