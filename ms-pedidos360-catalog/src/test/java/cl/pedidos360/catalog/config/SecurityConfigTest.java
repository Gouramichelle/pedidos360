package cl.pedidos360.catalog.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Verifica la lectura de roles y scopes desde los claims del token. */
class SecurityConfigTest {

    private final SecurityConfig config = new SecurityConfig(
            "https://login.microsoftonline.com/tenant/v2.0",
            List.of("api://mi-api"));

    private Jwt jwt(List<String> roles, String scp) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "abc-123")
                .claim("preferred_username", "operador@pedidos360.cl")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        if (roles != null) {
            builder.claim("roles", roles);
        }
        if (scp != null) {
            builder.claim("scp", scp);
        }
        return builder.build();
    }

    @Test
    @DisplayName("mapea el claim roles de Azure AD a authorities ROLE_*")
    void mapeaRoles() {
        var auth = config.jwtAuthenticationConverter().convert(jwt(List.of("Admin", "Operador"), null));
        assertThat(auth.getAuthorities().stream().map(GrantedAuthority::getAuthority))
                .containsExactlyInAnyOrder("ROLE_Admin", "ROLE_Operador");
    }

    @Test
    @DisplayName("mapea el claim scp separado por espacios a authorities SCOPE_*")
    void mapeaScopes() {
        var auth = config.jwtAuthenticationConverter().convert(jwt(null, "Pedidos.Read Pedidos.Write"));
        assertThat(auth.getAuthorities().stream().map(GrantedAuthority::getAuthority))
                .containsExactlyInAnyOrder("SCOPE_Pedidos.Read", "SCOPE_Pedidos.Write");
    }

    @Test
    @DisplayName("roles y scopes conviven en la misma autenticacion")
    void mapeaRolesYScopes() {
        var auth = config.jwtAuthenticationConverter().convert(jwt(List.of("Cliente"), "Pedidos.Read"));
        assertThat(auth.getAuthorities().stream().map(GrantedAuthority::getAuthority))
                .containsExactlyInAnyOrder("ROLE_Cliente", "SCOPE_Pedidos.Read");
    }

    @Test
    @DisplayName("usa preferred_username como nombre del principal")
    void usaPreferredUsername() {
        var auth = config.jwtAuthenticationConverter().convert(jwt(List.of("Cliente"), null));
        assertThat(auth.getName()).isEqualTo("operador@pedidos360.cl");
    }

    @Test
    @DisplayName("un token sin roles ni scopes queda autenticado pero sin authorities")
    void tokenSinClaims() {
        var auth = config.jwtAuthenticationConverter().convert(jwt(null, null));
        assertThat(auth.getAuthorities()).isEmpty();
    }
}
