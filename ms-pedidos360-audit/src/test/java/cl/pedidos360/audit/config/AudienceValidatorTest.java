package cl.pedidos360.audit.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AudienceValidatorTest {

    private static final List<String> ACCEPTED = List.of("api://mi-api", "guid-de-la-app");

    private Jwt tokenConAudiencia(String... aud) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("aud", List.of(aud))
                .claim("sub", "usuario-de-prueba")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claims(c -> c.putAll(Map.of()))
                .build();
    }

    @Test
    @DisplayName("acepta el formato api:// de los tokens v1")
    void aceptaAudienciaV1() {
        var result = new AudienceValidator(ACCEPTED).validate(tokenConAudiencia("api://mi-api"));
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("acepta el GUID pelado de los tokens v2")
    void aceptaAudienciaV2() {
        var result = new AudienceValidator(ACCEPTED).validate(tokenConAudiencia("guid-de-la-app"));
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("rechaza un token emitido para otra API")
    void rechazaAudienciaAjena() {
        var result = new AudienceValidator(ACCEPTED).validate(tokenConAudiencia("api://otra-api"));
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors().iterator().next().getErrorCode()).isEqualTo("invalid_token");
    }
}
