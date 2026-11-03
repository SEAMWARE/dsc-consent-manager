package com.seamware.consentmanager.service;

import com.seamware.consentmanager.domain.Participant;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

/**
 * The names one registered participant may be called by in a contract: the identifier it is
 * registered and authenticated under, and the self-description URI its catalog entry is published
 * at.
 *
 * <p>Contracts are written by components that do not share this service's identifier vocabulary. A
 * Contract Service projecting TM Forum agreements names the parties by their catalog
 * self-description URL, while a participant registers here - and presents a token - under its DID.
 * Both name the same row, so a party a contract states is matched against either name rather than
 * against the identifier alone (ADR-0017).
 *
 * <p>A blank or absent name never matches, so a participant that published no self-description is
 * named by its identifier alone and a contract that states no party is bound by nobody.
 *
 * @param identifier the identifier the participant is registered under
 * @param selfDescriptionUri the URI its self-description is published at, when it declared one
 */
public record PartyAlias(@NonNull String identifier, @Nullable String selfDescriptionUri) {

    /** The names the registered participant goes by. */
    @NonNull
    public static PartyAlias of(@NonNull Participant participant) {
        return new PartyAlias(participant.getIdentifier(), participant.getSelfDescriptionUri());
    }

    /**
     * Whether the party a contract states is this participant under either of its names.
     *
     * @param statedParty the party as the contract states it, possibly absent
     * @return {@code true} when the contract names this participant
     */
    public boolean names(@Nullable String statedParty) {
        if (statedParty == null || statedParty.isBlank()) {
            return false;
        }
        return statedParty.equals(identifier) || statedParty.equals(selfDescriptionUri);
    }
}
