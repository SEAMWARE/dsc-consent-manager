package com.seamware.consentmanager.client;

import com.seamware.consentmanager.client.dto.ContractResponse;
import com.seamware.consentmanager.config.ConsentManagerConfiguration;
import com.seamware.consentmanager.error.UpstreamServiceException;
import io.micronaut.cache.CacheManager;
import io.micronaut.cache.SyncCache;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.codec.CodecException;
import jakarta.inject.Singleton;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves a contract URI via the ecosystem, then the bilateral endpoint; the only contract cache.
 * Cached by hand, not {@code @Cacheable}, so a miss or a failure is never stored.
 */
@Singleton
public class ContractGateway {

    /** Name of the cache declared under {@code micronaut.caches.contracts}. */
    public static final String CACHE_NAME = "contracts";

    private static final char PATH_SEPARATOR = '/';

    /** Characters that end a URI's path; a query or fragment is no part of the identifier. */
    private static final String PATH_TERMINATORS = "?#";

    /**
     * Statuses with which an upstream says it does not serve the ecosystem endpoint at all. A
     * Contract Service that keeps only bilateral contracts answers one of these instead of the 404
     * that means "no such contract", so both have to lead on to the bilateral endpoint rather than
     * count as a failure.
     */
    private static final Set<Integer> ECOSYSTEM_ENDPOINT_ABSENT =
            Set.of(HttpStatus.METHOD_NOT_ALLOWED.getCode(), HttpStatus.NOT_IMPLEMENTED.getCode());

    private final ContractServiceClient client;
    private final SyncCache<?> cache;
    private final String upstreamUrl;

    /** Creates the gateway over the declarative client and the {@code contracts} cache. */
    public ContractGateway(
            ContractServiceClient client,
            CacheManager<?> cacheManager,
            ConsentManagerConfiguration.ContractService configuration) {
        this.client = client;
        this.cache = cacheManager.getCache(CACHE_NAME);
        this.upstreamUrl = configuration.getUrl();
    }

    /**
     * The contract the URI names, cached when possible; empty when neither endpoint knows it.
     *
     * @throws UpstreamServiceException when unreachable, failing past retries or undecodable
     */
    public Optional<ContractResponse> fetch(String contractUri) {
        Optional<ContractResponse> cached = cache.get(contractUri, ContractResponse.class);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<ContractResponse> fetched = fetchUpstream(contractUri);
        fetched.ifPresent(contract -> cache.put(contractUri, contract));
        return fetched;
    }

    /**
     * The upstream identifier: the last path segment, query and fragment dropped, or the whole URI.
     * The authority is deliberately unchecked; never applied to the stored, verbatim URI.
     */
    static String contractIdOf(String contractUri) {
        int end =
                PATH_TERMINATORS
                        .chars()
                        .map(contractUri::indexOf)
                        .filter(terminator -> terminator >= 0)
                        .min()
                        .orElse(contractUri.length());
        String path = contractUri.substring(0, end);
        while (!path.isEmpty() && path.charAt(path.length() - 1) == PATH_SEPARATOR) {
            path = path.substring(0, path.length() - 1);
        }
        int lastSeparator = path.lastIndexOf(PATH_SEPARATOR);
        String identifier = lastSeparator < 0 ? path : path.substring(lastSeparator + 1);
        return identifier.isEmpty() ? contractUri : identifier;
    }

    /** Tries the ecosystem endpoint, then the bilateral one; empty when neither knows the id. */
    private Optional<ContractResponse> fetchUpstream(String contractUri) {
        String contractId = contractIdOf(contractUri);
        try {
            Optional<ContractResponse> ecosystem = fetchEcosystem(contractId);
            return ecosystem.isPresent() ? ecosystem : client.fetchBilateralContract(contractId);
        } catch (HttpClientException | CodecException e) {
            throw new UpstreamServiceException(
                    "Contract Service at %s failed for contract %s"
                            .formatted(upstreamUrl, contractUri),
                    e);
        }
    }

    /**
     * The ecosystem contract, empty when the upstream knows no such contract or serves no ecosystem
     * endpoint at all. Only this endpoint may be absent: the bilateral call that follows raises
     * whatever it is answered with, so an upstream serving neither shape stays an upstream failure
     * instead of silently reading as an unknown contract.
     */
    private Optional<ContractResponse> fetchEcosystem(String contractId) {
        try {
            return client.fetchContract(contractId);
        } catch (HttpClientResponseException e) {
            if (!ECOSYSTEM_ENDPOINT_ABSENT.contains(e.getStatus().getCode())) {
                throw e;
            }
            return Optional.empty();
        }
    }
}
