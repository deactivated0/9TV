package com.deactivated0.tv9.requests

import android.text.TextUtils
import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap


class DnsCache : Dns {
    private val dnsCache: MutableMap<String, List<InetAddress>> = ConcurrentHashMap()

    override fun lookup(hostname: String): List<InetAddress> {
        if (TextUtils.isEmpty(hostname)) {
            return Dns.SYSTEM.lookup(hostname)
        }

        dnsCache[hostname]?.let {
            return it
        }

        val addresses = InetAddress.getAllByName(hostname)
        val addressesNew = ArrayList<InetAddress>(addresses.size)

        // Single pass: add IPv4 addresses first, then non-IPv4, avoiding O(N^2) ArrayList.add(0, x) shifts
        for (address in addresses) {
            if (address is Inet4Address) {
                addressesNew.add(address)
            }
        }
        for (address in addresses) {
            if (address !is Inet4Address) {
                addressesNew.add(address)
            }
        }

        if (addressesNew.isNotEmpty()) {
            dnsCache[hostname] = addressesNew
        }

        return addressesNew
    }
}