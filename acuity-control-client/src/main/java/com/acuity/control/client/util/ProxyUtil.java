package com.acuity.control.client.util;

import com.acuity.control.client.AbstractBotController;
import com.acuity.control.client.websockets.response.MessageResponse;
import com.acuity.db.domain.vertex.impl.message_package.MessagePackage;
import com.acuity.db.domain.vertex.impl.proxy.Proxy;
import com.acuity.db.util.SecuityUtil;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.concurrent.TimeUnit;

/**
 * Created by Zachary Herridge on 8/15/2017.
 */
public class ProxyUtil {

    public static void setSocksProxy(Proxy proxy, AbstractBotController botController){
        System.clearProperty("socksProxyHost");
        System.clearProperty("socksProxyPort");
        System.clearProperty("java.net.socks.username");
        System.clearProperty("java.net.socks.password");
        Authenticator.setDefault(null);

        if (proxy != null) {
            System.setProperty("socksProxyHost", proxy.getHost());
            System.setProperty("socksProxyPort", String.valueOf(proxy.getPort()));

            if (proxy.getUsername() != null) {
                System.setProperty("java.net.socks.username", proxy.getUsername());
                Authenticator.setDefault(new ProxyAuth(proxy.getUsername(), null));
            }

            if (proxy.getPassword() != null) {
                botController.decryptString(proxy.getPassword()).ifPresent(password -> {
                    System.setProperty("java.net.socks.password", password);
                    Authenticator.setDefault(new ProxyAuth(proxy.getUsername(), password));
                });
            }
        }
    }

    public static class ProxyAuth extends Authenticator {
        private PasswordAuthentication auth;

        ProxyAuth(String user, String password) {
            auth = new PasswordAuthentication(user, password == null ? new char[]{} : password.toCharArray());
        }

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            return auth;
        }
    }
}