package com.seamware.consentmanager.client;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.http.client.HttpClientConfiguration;
import jakarta.inject.Singleton;
import java.net.Proxy;

/**
 * Makes {@code proxy-type: DIRECT} mean "no proxy" on every configured HTTP client.
 *
 * <p>Micronaut falls back to {@code micronaut.http.client.*} for any key a per-service block under
 * {@code micronaut.http.services.<id>.*} leaves unset. A deployment that proxies by default and
 * exempts one service - the Contract Service behind a network policy the proxy may not cross, say -
 * therefore keeps the global {@code proxy-address} alongside the DIRECT type it just set. {@code
 * HttpClientConfiguration.resolveProxy} then builds {@code new Proxy(DIRECT, address)}, which
 * {@link Proxy} refuses: an {@link IllegalArgumentException} on every request that client makes,
 * reaching the caller as a 500 and naming nothing that would point at the configuration.
 *
 * <p>Dropping the inherited address is what the exemption asked for, and costs nothing where none
 * was inherited: a client left at the DIRECT default never had one.
 */
@Singleton
public class DirectProxyNormalizer implements BeanCreatedEventListener<HttpClientConfiguration> {

    @Override
    public HttpClientConfiguration onCreated(BeanCreatedEvent<HttpClientConfiguration> event) {
        HttpClientConfiguration configuration = event.getBean();
        if (configuration.getProxyType() == Proxy.Type.DIRECT) {
            configuration.setProxyAddress(null);
        }
        return configuration;
    }
}
