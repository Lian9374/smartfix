package com.smartfix.auth.security;

import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.dto.UserAuthenticationData;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SmartFixUserDetailsSerializationTest {
    @Test
    void serializedSessionPreservesIdentityButNeverRestoresCredentialHash() throws Exception {
        String syntheticHash = "fixture-hash-not-a-real-credential";
        var principal = new SmartFixUserDetails(new UserAuthenticationData(
                42L, "fixture.requester", syntheticHash, Role.REQUESTER, AccountStatus.ACTIVE, 7L));
        var bytes = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(bytes)) {
            output.writeObject(principal);
        }
        assertThat(bytes.toString(StandardCharsets.ISO_8859_1)).doesNotContain(syntheticHash);
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            var restored = (SmartFixUserDetails) input.readObject();
            assertThat(restored.getUserId()).isEqualTo(42L);
            assertThat(restored.getUsername()).isEqualTo("fixture.requester");
            assertThat(restored.getRole()).isEqualTo(Role.REQUESTER);
            assertThat(restored.getSecurityVersion()).isEqualTo(7L);
            assertThat(restored.isEnabled()).isTrue();
            assertThat(restored.getPassword()).isNull();
        }
    }
}
