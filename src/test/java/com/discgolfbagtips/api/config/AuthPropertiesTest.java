package com.discgolfbagtips.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthPropertiesTest {

    /** The issuer must match the token's {@code iss} exactly, so a pasted trailing slash cannot leak in. */
    @Test
    void derivesIssuerAndKeySetFromTheProjectUrl() {
        var auth = new BagTipsProperties.Auth("https://abc.supabase.co/", "authenticated");

        assertThat(auth.enabled()).isTrue();
        assertThat(auth.issuer()).isEqualTo("https://abc.supabase.co/auth/v1");
        assertThat(auth.jwkSetUri()).isEqualTo("https://abc.supabase.co/auth/v1/.well-known/jwks.json");
    }

    @Test
    void aBlankUrlMeansAccountsAreOff() {
        assertThat(new BagTipsProperties.Auth("", "authenticated").enabled()).isFalse();
    }
}
