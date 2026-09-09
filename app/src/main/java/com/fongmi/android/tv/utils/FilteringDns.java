package com.fongmi.android.tv.utils;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import okhttp3.Dns;

/**
 * DNS resolver that refuses to return private/loopback addresses. Used by outbound proxies that
 * fetch attacker-influenced URLs: validating the hostname before the request is not enough, the
 * resolved address must be checked at connection time too, or a TTL=0 DNS rebinding answers the
 * first lookup with a public IP and the second with an internal one.
 */
public class FilteringDns implements Dns {

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        List<InetAddress> addresses = com.github.catvod.net.OkHttp.dns().lookup(hostname);
        for (InetAddress address : addresses) {
            if (UrlSafety.isBlockedAddress(address)) throw new UnknownHostException("Private address not allowed: " + hostname);
        }
        return addresses;
    }
}
