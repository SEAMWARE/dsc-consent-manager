# Deployment

[README](../README.md) › [Documentation](README.md) › Deployment

The Consent Manager ships as one container image (`docker/Dockerfile`) and runs as a stateless OAuth2
resource server against PostgreSQL 16 and one or more trusted OIDC identity providers.
Released images are published as `quay.io/fiware/consent-manager:<version>` for `linux/amd64` and
`linux/arm64`; the versions are listed on the GitHub releases page, and [CI and releases](ci.md)
describes how they are built. Pull-request builds are tagged `<version>-PRE-<pr>` and are for testing only.

| Target | Start here |
| --- | --- |
| Kubernetes | [`charts/consent-manager/README.md`](../charts/consent-manager/README.md): install, required inputs, values, key rotation, probes, migrations |
| Local stack (Compose) | [`docker/README.md`](../docker/README.md): PostgreSQL, Keycloak, WireMock and a smoke test |
| Every environment variable | [`.env.sample`](../.env.sample): meaning, default, and the key-generation commands |

Both targets run the image the same way: a numeric non-root user, a read-only root filesystem with a
writable `/tmp`, and health and metrics on the management port (`9090`) at the origin root, apart
from the API port. Credentials come from a Secret and receipt signing keys from mounted files, never
from a ConfigMap or the image.

Participant webhooks (ADR-0016) need no extra component: an outbox table drained by every replica,
configured by the `PARTICIPANT_NOTIFICATIONS_*` variables (chart: `config.participantNotifications`).
Every replica may deliver, since claims are leased and never overlap. Set `PARTICIPANT_NOTIFICATIONS_ENABLED=false`
to keep a replica from delivering; the sweep that drops undelivered payloads after
`PARTICIPANT_NOTIFICATIONS_MAX_AGE` runs regardless. Startup refuses timing values that cannot
hold together (a lease too short for a batch, a per-participant cap not below the concurrency, a
max age shorter than the retry schedule). Webhook URLs are participant-supplied, so egress must reach
the public internet; `PARTICIPANT_NOTIFICATIONS_ALLOWED_HOSTS` narrows it to listed hosts.
Integrators read [`participant-notifications.md`](participant-notifications.md).

The Contract Service `CONTRACT_SERVICE_URL` names is read on two endpoints: a contract URI's last
path segment is looked up first under `/contracts/{id}` (ecosystem shape) and, when that is a miss,
under `/bilaterals/{id}` (bilateral shape). An upstream holding only bilateral contracts needs no
configuration to say so - a `404`, `405` or `501` on the ecosystem endpoint moves the lookup on to
the bilateral one instead of failing it. Everything else is an upstream failure: a `5xx` other than
`501`, or a transport failure, is retried to the `CONTRACT_SERVICE_RETRY_*` budget and then
surfaces as `502`, and so does the bilateral endpoint refusing the request the ecosystem one
deferred.

A contract may name its parties by the participant identifiers this service registers, or by the
`selfDescriptionUri` each participant published - a Contract Service that projects catalog
documents names them by the latter. Either resolves to the same registration, so no configuration
chooses between them, and the notice records the participant identifier whichever the contract used
(ADR-0017). A self-description URI claimed by two active registrations names neither: generation
answers `404`, and the duplicate registration is the thing to fix.

Outbound HTTP goes through a proxy when `MICRONAUT_HTTP_CLIENT_PROXY_TYPE` and
`MICRONAUT_HTTP_CLIENT_PROXY_ADDRESS` are set - needed where an identity provider is reachable only
under its public name. That block is the default for every client, and one client is exempted by
setting `MICRONAUT_HTTP_SERVICES_<SERVICE>_PROXY_TYPE=DIRECT`
(`MICRONAUT_HTTP_SERVICES_CONTRACT_SERVICE_PROXY_TYPE` for the Contract Service, which a network
policy may admit this service to and not the proxy). The exemption needs nothing else: the global
address is dropped for that client rather than inherited beside the DIRECT type.

## Related documents

- [`security.md`](security.md): the token, health and signing-key model.
- [ADR-0014](adr/0014-a-grant-receipt-is-signed-once-and-travels-beside-its-signature.md): why
  receipt-key rotation takes two deploys.
- [Participant notifications](participant-notifications.md): the webhook contract the outbox
  delivers to.
