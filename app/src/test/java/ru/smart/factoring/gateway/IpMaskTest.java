package ru.smart.factoring.gateway;

import org.junit.jupiter.api.Test;
import ru.smart.factoring.gateway.routing.service.config.IpMask;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Маскирование IP в access log: последний октет IPv4, всё после первых 48 бит IPv6, не адрес - «?».
 */
class IpMaskTest {

    @Test
    void ipv4LosesLastOctet() {
        assertThat(IpMask.mask("203.0.113.7")).isEqualTo("203.0.113.0");
        assertThat(IpMask.mask(" 10.1.2.255 ")).isEqualTo("10.1.2.0");
    }

    @Test
    void ipv6KeepsFirst48Bits() {
        assertThat(IpMask.mask("2001:db8:85a3:1234:5678::7")).isEqualTo("2001:db8:85a3:0:0:0:0:0");
        assertThat(IpMask.mask("::1")).isEqualTo("0:0:0:0:0:0:0:0");
    }

    @Test
    void forwardedForMasksEveryAddress() {
        assertThat(IpMask.maskList("203.0.113.7, 172.18.0.5")).isEqualTo("203.0.113.0,172.18.0.0");
    }

    @Test
    void anythingButAnAddressIsNotWritten() {
        // клиент может подставить в X-Forwarded-For любой текст, в том числе имя хоста - DNS не запрашивается
        assertThat(IpMask.maskList("Иванов Иван, example.com, 999.1.1.1, unknown")).isEqualTo("?,?,?,?");
    }
}
