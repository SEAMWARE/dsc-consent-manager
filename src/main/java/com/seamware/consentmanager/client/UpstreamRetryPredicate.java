package com.seamware.consentmanager.client;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.retry.annotation.RetryPredicate;

/**
 * Retries a 5xx or a transport failure only; a 4xx is a request defect retrying cannot fix.
 * Instantiated by the retry interceptor: keep the public no-arg constructor and no injected state.
 *
 * <p>501 is the one 5xx left out. It is a statement about the endpoint rather than a report of an
 * outage, so every further attempt would be answered the same way - and the caller handles it:
 * {@code ContractGateway} reads it as "this upstream has no such endpoint" and moves on.
 */
public final class UpstreamRetryPredicate implements RetryPredicate {

    /** Lowest status code that means the upstream failed rather than refused. */
    private static final int LOWEST_SERVER_ERROR = HttpStatus.INTERNAL_SERVER_ERROR.getCode();

    /** The 5xx that says the endpoint does not exist here, which no later attempt changes. */
    private static final int NOT_IMPLEMENTED = HttpStatus.NOT_IMPLEMENTED.getCode();

    @Override
    public boolean test(Throwable throwable) {
        if (throwable instanceof HttpClientResponseException response) {
            int status = response.getStatus().getCode();
            return status >= LOWEST_SERVER_ERROR && status != NOT_IMPLEMENTED;
        }
        // Timeouts and other transport failures, which a later attempt may survive.
        return throwable instanceof HttpClientException;
    }
}
