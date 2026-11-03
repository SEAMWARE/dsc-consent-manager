package com.seamware.consentmanager.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.seamware.consentmanager.client.dto.ContractResponse;
import com.seamware.consentmanager.support.ContractFixtures;
import com.seamware.consentmanager.support.ContractServiceStub;
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.ServiceHttpClientConfiguration;
import io.micronaut.inject.qualifiers.Qualifiers;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins that a client configured DIRECT carries no proxy address, whether it was configured globally
 * or inherited the address from the global block while exempting itself.
 */
@DisplayName("Direct HTTP client proxy configuration")
class DirectProxyNormalizerTest {

    /** Service id of the Contract Service client, the one a deployment exempts from the proxy. */
    private static final String CONTRACT_SERVICE = ContractServiceClient.SERVICE_ID;

    /** A proxy nothing listens on: reaching for it at all is the failure this guards against. */
    private static final String UNUSABLE_PROXY = "127.0.0.1:1";

    /** Contract URI under test; its last segment is the id the upstream is asked for. */
    private static final String CONTRACT_URI = "https://contract.example.org/v1/contracts/c-1";

    /** Identifier derived from that URI. */
    private static final String CONTRACT_ID = "c-1";

    @Test
    @DisplayName("a service exempted from the global proxy does not inherit its address")
    void anExemptedServiceClientDropsTheInheritedAddress() {
        try (ContractServiceStub stub = new ContractServiceStub();
                ApplicationContext context =
                        ContractGatewayTest.startContext(stub, proxiedExcept(CONTRACT_SERVICE))) {
            stub.serveEcosystemContract(
                    CONTRACT_ID, ContractFixtures.body(ContractFixtures.ECOSYSTEM));

            assertThat(
                            context.getBean(
                                            ServiceHttpClientConfiguration.class,
                                            Qualifiers.byName(CONTRACT_SERVICE))
                                    .getProxyAddress())
                    .as("DIRECT plus an address is a java.net.Proxy no client can be built from")
                    .isEmpty();
            assertThat(
                            context.getBean(ContractGateway.class)
                                    .fetch(CONTRACT_URI)
                                    .orElseThrow()
                                    .shape())
                    .as("the exempted client has to reach its upstream, not the global proxy")
                    .isEqualTo(ContractResponse.Shape.ECOSYSTEM);
        }
    }

    @Test
    @DisplayName("a globally DIRECT client drops an address configured beside it")
    void aDirectGlobalClientDropsItsAddress() {
        try (ContractServiceStub stub = new ContractServiceStub();
                ApplicationContext context =
                        ContractGatewayTest.startContext(
                                stub,
                                Map.of(
                                        "micronaut.http.client.proxy-type",
                                        "DIRECT",
                                        "micronaut.http.client.proxy-address",
                                        UNUSABLE_PROXY))) {
            assertThat(context.getBean(DefaultHttpClientConfiguration.class).getProxyAddress())
                    .as("the global block is normalised on the same rule as a per-service one")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("a client that asked for a proxy keeps it")
    void aProxiedClientKeepsItsAddress() {
        try (ContractServiceStub stub = new ContractServiceStub();
                ApplicationContext context =
                        ContractGatewayTest.startContext(
                                stub,
                                Map.of(
                                        "micronaut.http.client.proxy-type",
                                        "HTTP",
                                        "micronaut.http.client.proxy-address",
                                        UNUSABLE_PROXY))) {
            assertThat(context.getBean(DefaultHttpClientConfiguration.class).getProxyAddress())
                    .as("only DIRECT means no proxy; every other type keeps what it was given")
                    .isNotEmpty();
        }
    }

    /**
     * Proxies every client through an unusable proxy, except the named service, which is DIRECT.
     */
    private static Map<String, Object> proxiedExcept(String serviceId) {
        return Map.of(
                "micronaut.http.client.proxy-type",
                "HTTP",
                "micronaut.http.client.proxy-address",
                UNUSABLE_PROXY,
                "micronaut.http.services." + serviceId + ".proxy-type",
                "DIRECT");
    }
}
