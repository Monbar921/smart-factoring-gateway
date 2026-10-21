package ru.smart.factoring.gateway.routing.service.config;

import org.springframework.boot.reactor.netty.NettyServerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.netty.http.server.logging.AccessLog;
import reactor.netty.http.server.logging.AccessLogFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * Access log на уровне Netty: пишется после отправки ответа для любого запроса, в том числе 404 и ошибок проксирования,
 * поэтому статус в логе - тот, что реально получил клиент. Логгер - reactor.netty.http.server.AccessLog.
 * <p>
 * Формат: {@code ip=203.0.113.7 xff=- "GET /api/deals?x=1 HTTP/1.1" 200 512b 12ms}.
 * xff - заголовок X-Forwarded-For как есть: клиент может подставить в него что угодно,
 * поэтому реальный адрес соединения пишется отдельно в ip.
 */
@Configuration
public class AccessLogConfiguration {
    private static final String HEALTH_URI = "/actuator/health";
    private static final String NO_VALUE = "-";

    @Bean
    public NettyServerCustomizer accessLogCustomizer() {
        return server -> server.accessLog(true, AccessLogFactory.createFilter(
                // healthcheck docker дёргает каждые 10 с, в логе он только шум
                args -> !String.valueOf(args.uri()).startsWith(HEALTH_URI),
                args -> AccessLog.create("ip={} xff={} \"{} {} {}\" {} {}b {}ms",
                        clientIp(args.remoteAddress()),
                        valueOrDash(args.requestHeader("X-Forwarded-For")),
                        args.method(),
                        args.uri(),
                        args.protocol(),
                        args.status(),
                        args.contentLength(),
                        args.duration())));
    }

    private static String clientIp(final SocketAddress address) {
        if (address instanceof InetSocketAddress inet) {
            return inet.getAddress() != null ? inet.getAddress().getHostAddress() : inet.getHostString();
        }
        return address != null ? address.toString() : NO_VALUE;
    }

    private static Object valueOrDash(final CharSequence value) {
        return value != null && !value.isEmpty() ? value : NO_VALUE;
    }
}
