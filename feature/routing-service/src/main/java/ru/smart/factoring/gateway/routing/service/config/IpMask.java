package ru.smart.factoring.gateway.routing.service.config;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Маскирование IP-адресов для access log (152-ФЗ, минимизация): IP посетителя вместе со временем и запросом -
 * персональные данные, а для разбора сбоев достаточно сети. IPv4 - обнуляется последний октет ({@code 203.0.113.0}),
 * IPv6 - остаются первые 48 бит ({@code 2001:db8:85a3:0:0:0:0:0}).
 * <p>
 * Строка разбирается только как литерал адреса ({@link InetAddress#ofLiteral}, без DNS); IPv4 - строго четыре
 * октета. Всё остальное (в X-Forwarded-For клиент может подставить что угодно) пишется как {@code ?}.
 */
public final class IpMask {
    static final String UNKNOWN = "?";
    private static final Pattern IPV4 = Pattern.compile("(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}");
    private static final Pattern IPV6 = Pattern.compile("[0-9a-fA-F:]*:[0-9a-fA-F:.]*");
    private static final int IPV6_KEPT_BYTES = 6;

    private IpMask() {
    }

    public static String mask(final InetAddress address) {
        final byte[] bytes = address.getAddress();
        Arrays.fill(bytes, address instanceof Inet4Address ? bytes.length - 1 : IPV6_KEPT_BYTES, bytes.length, (byte) 0);
        try {
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (final UnknownHostException e) {
            return UNKNOWN; // длина массива та же, что у исходного адреса - сюда не попадаем
        }
    }

    /**
     * Маскирует строку адреса; не адрес - {@code ?}.
     */
    public static String mask(final String value) {
        final String ip = value.strip();
        if (!IPV4.matcher(ip).matches() && !IPV6.matcher(ip).matches()) {
            return UNKNOWN;
        }
        try {
            return mask(InetAddress.ofLiteral(ip));
        } catch (final IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

    /**
     * Маскирует каждый адрес списка X-Forwarded-For ({@code клиент, прокси1, прокси2}).
     */
    public static String maskList(final CharSequence header) {
        return Arrays.stream(header.toString().split(",")).map(IpMask::mask).collect(Collectors.joining(","));
    }
}
