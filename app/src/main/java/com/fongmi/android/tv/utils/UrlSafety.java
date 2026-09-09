package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;

/**
 * SSRF guard for outbound URLs taken from remote/untrusted sources.
 *
 * <p>Only permits public http/https targets; rejects private, loopback, link-local and
 * site-local addresses to prevent requests reaching internal services, the device itself,
 * or cloud metadata endpoints.</p>
 */
public class UrlSafety {

    private UrlSafety() {
    }

    public static boolean isSafeHttpUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String scheme;
        String host;
        try {
            URI uri = URI.create(url);
            scheme = uri.getScheme();
            host = uri.getHost();
        } catch (Throwable e) {
            return false;
        }
        if (scheme == null || host == null || host.isEmpty()) return false;
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return false;
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) if (isBlockedAddress(address)) return false;
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * True for addresses that outbound proxies must never connect to: loopback, link-local,
     * site-local (LAN), plus the CGNAT 100.64.0.0/10, benchmark/fake-ip 198.18.0.0/15 and
     * well-known NAT64 64:ff9b::/96 ranges that plain InetAddress flags miss.
     */
    public static boolean isBlockedAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()) return true;
        if (address instanceof Inet4Address) {
            byte[] bytes = address.getAddress();
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            if (first == 100 && second >= 64 && second <= 127) return true;
            return first == 198 && (second == 18 || second == 19);
        }
        if (address instanceof Inet6Address) {
            byte[] bytes = address.getAddress();
            return bytes[0] == 0 && bytes[1] == 0x64 && bytes[2] == (byte) 0xff && bytes[3] == (byte) 0x9b;
        }
        return false;
    }

    /**
     * Relaxed guard for media stream URLs (HLS/DASH segments). LAN streams (site-local
     * addresses) are legitimate playback sources, but the loopback device and link-local
     * (cloud metadata 169.254.169.254) targets remain blocked.
     */
    public static boolean isSafeMediaUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String scheme;
        String host;
        try {
            URI uri = URI.create(url);
            scheme = uri.getScheme();
            host = uri.getHost();
        } catch (Throwable e) {
            return false;
        }
        if (scheme == null || host == null || host.isEmpty()) return false;
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return false;
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()) return false;
            }
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Returns true only for a loopback origin on the app's exact local server port. Used to
     * decide whether a browser Origin may receive a credentialed CORS response. A missing port
     * (default 80) is deliberately rejected so any unrelated local service on port 80 stays
     * outside the credentialed CORS whitelist.
     */
    public static boolean isLoopbackOrigin(String origin, int proxyPort) {
        if (TextUtils.isEmpty(origin) || "null".equals(origin)) return false;
        try {
            URI uri = URI.create(origin);
            String host = uri.getHost();
            if (host == null) return false;
            int port = uri.getPort();
            return "http".equalsIgnoreCase(uri.getScheme()) && ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "::1".equals(host) || "[::1]".equals(host)) && port == proxyPort;
        } catch (Throwable e) {
            return false;
        }
    }
}
