package com.acuity.control.client.util;

import com.acuity.common.account_creator.captcha.ProxyType;
import com.acuity.common.util.IPUtil;
import com.acuity.control.client.BotControl;
import com.acuity.db.domain.vertex.impl.proxy.Proxy;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.Optional;

/**
 * Created by Zachary Herridge on 8/15/2017.
 */
public class ProxyUtil {

    public static void setSocksProxy(Proxy proxy, BotControl botControl){
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
                botControl.getConnection().decryptString(proxy.getPassword()).ifPresent(password -> {
                    System.setProperty("java.net.socks.password", password);
                    Authenticator.setDefault(new ProxyAuth(proxy.getUsername(), password));
                });
            }
        }
    }

    public static void setSocksProxy(Proxy proxy, String password){
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

            if (password != null) {
                System.setProperty("java.net.socks.password", password);
                Authenticator.setDefault(new ProxyAuth(proxy.getUsername(), password));
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

    public static void main(String[] args) {
        setSocksProxy(new Proxy(null, ProxyType.SOCKS5, "45.57.180.70", 8000, "9S4cme", null), "H25zGc");
        Optional<String> ip = IPUtil.getIP();
        System.out.println(ip);
    }
}
