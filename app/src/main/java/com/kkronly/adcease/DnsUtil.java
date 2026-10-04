package com.kkronly.adcease;

import java.nio.ByteBuffer;

/**
 * Minimal IPv4/UDP/DNS packet helpers — enough to run a transparent
 * DNS sinkhole inside a VpnService tun device.
 *
 * Developer: Kaustav Kanti Ray @iamkkronly
 */
public final class DnsUtil {

    /** Developer: Kaustav Kanti Ray @iamkkronly */
    private static final String CREDIT = Prefs.DEVELOPER;

    private DnsUtil() {}

    // ---------- IPv4 ----------

    public static boolean isIpv4Udp(byte[] p, int len) {
        if (len < 28) return false;
        if (((p[0] & 0xF0) >> 4) != 4) return false;
        return (p[9] & 0xFF) == 17;
    }

    public static int ihl(byte[] p) { return (p[0] & 0x0F) * 4; }

    public static int srcPort(byte[] p) { int o = ihl(p); return ((p[o] & 0xFF) << 8) | (p[o + 1] & 0xFF); }

    public static int dstPort(byte[] p) { int o = ihl(p); return ((p[o + 2] & 0xFF) << 8) | (p[o + 3] & 0xFF); }

    public static byte[] udpPayload(byte[] p, int len) {
        int o = ihl(p) + 8;
        int n = len - o;
        if (n <= 0) return new byte[0];
        byte[] out = new byte[n];
        System.arraycopy(p, o, out, 0, n);
        return out;
    }

    /** Builds a reply packet: swaps addresses/ports of {@code req} and carries {@code payload}. */
    public static byte[] buildReply(byte[] req, byte[] payload) {
        int hl = ihl(req);
        int total = hl + 8 + payload.length;
        byte[] out = new byte[total];
        System.arraycopy(req, 0, out, 0, hl);

        out[2] = (byte) (total >> 8);
        out[3] = (byte) total;
        out[4] = 0; out[5] = 0;       // id
        out[6] = 0x40; out[7] = 0;    // don't fragment
        out[8] = 64;                  // ttl

        // swap src/dst IP
        for (int i = 0; i < 4; i++) {
            out[12 + i] = req[16 + i];
            out[16 + i] = req[12 + i];
        }
        out[10] = 0; out[11] = 0;
        int ipck = checksum(out, 0, hl);
        out[10] = (byte) (ipck >> 8); out[11] = (byte) ipck;

        // UDP header, ports swapped
        int ro = ihl(req);
        out[hl]     = req[ro + 2]; out[hl + 1] = req[ro + 3];
        out[hl + 2] = req[ro];     out[hl + 3] = req[ro + 1];
        int ulen = 8 + payload.length;
        out[hl + 4] = (byte) (ulen >> 8); out[hl + 5] = (byte) ulen;
        out[hl + 6] = 0; out[hl + 7] = 0;
        System.arraycopy(payload, 0, out, hl + 8, payload.length);

        int uck = udpChecksum(out, hl, ulen);
        if (uck == 0) uck = 0xFFFF;
        out[hl + 6] = (byte) (uck >> 8); out[hl + 7] = (byte) uck;
        return out;
    }

    private static int checksum(byte[] b, int off, int len) {
        long sum = 0;
        for (int i = 0; i < len - 1; i += 2) sum += ((b[off + i] & 0xFF) << 8) | (b[off + i + 1] & 0xFF);
        if ((len & 1) != 0) sum += (b[off + len - 1] & 0xFF) << 8;
        while ((sum >> 16) != 0) sum = (sum & 0xFFFF) + (sum >> 16);
        return (int) (~sum & 0xFFFF);
    }

    private static int udpChecksum(byte[] pkt, int udpOff, int udpLen) {
        long sum = 0;
        for (int i = 12; i < 20; i += 2) sum += ((pkt[i] & 0xFF) << 8) | (pkt[i + 1] & 0xFF); // pseudo-header addrs
        sum += 17;
        sum += udpLen;
        for (int i = 0; i < udpLen - 1; i += 2)
            sum += ((pkt[udpOff + i] & 0xFF) << 8) | (pkt[udpOff + i + 1] & 0xFF);
        if ((udpLen & 1) != 0) sum += (pkt[udpOff + udpLen - 1] & 0xFF) << 8;
        while ((sum >> 16) != 0) sum = (sum & 0xFFFF) + (sum >> 16);
        return (int) (~sum & 0xFFFF);
    }

    // ---------- DNS ----------

    /** Extracts the QNAME of the first question, or null. */
    public static String question(byte[] dns) {
        try {
            if (dns.length < 13) return null;
            int qd = ((dns[4] & 0xFF) << 8) | (dns[5] & 0xFF);
            if (qd < 1) return null;
            StringBuilder sb = new StringBuilder();
            int i = 12;
            while (i < dns.length) {
                int l = dns[i] & 0xFF;
                if (l == 0) break;
                if ((l & 0xC0) != 0) return null; // no compression in a question
                i++;
                if (i + l > dns.length) return null;
                if (sb.length() > 0) sb.append('.');
                sb.append(new String(dns, i, l, java.nio.charset.StandardCharsets.US_ASCII));
                i += l;
            }
            return sb.length() == 0 ? null : sb.toString();
        } catch (Exception e) { return null; }
    }

    /** Builds an NXDOMAIN answer echoing the question section of {@code query}. */
    public static byte[] nxdomain(byte[] query) {
        int end = 12;
        while (end < query.length && (query[end] & 0xFF) != 0) end += (query[end] & 0xFF) + 1;
        end += 1 + 4; // null label + qtype + qclass
        if (end > query.length) end = query.length;
        ByteBuffer b = ByteBuffer.allocate(end);
        b.put(query, 0, end);
        byte[] out = b.array();
        out[2] = (byte) 0x81;        // QR=1, RD copied loosely
        out[3] = (byte) 0x83;        // RA=1, RCODE=3 (NXDOMAIN)
        out[6] = 0; out[7] = 0;      // ANCOUNT
        out[8] = 0; out[9] = 0;      // NSCOUNT
        out[10] = 0; out[11] = 0;    // ARCOUNT
        return out;
    }
}
