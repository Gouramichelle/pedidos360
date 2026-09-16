package cl.pedidos360.report.config;

import java.util.List;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Valida el claim "aud" del token contra la lista de audiencias aceptadas.
 *
 * Se acepta mas de un valor a proposito: Azure AD emite tokens v1 con
 * aud = "api://<client-id>" y tokens v2 con aud = "<client-id>" (GUID pelado).
 * Configurar ambos en SECURITY_AUDIENCES evita 401 silenciosos al cambiar
 * accessTokenAcceptedVersion en el manifiesto de la App Registration.
 */
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final List<String> accepted;

    public AudienceValidator(List<String> accepted) {
        this.accepted = accepted;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        List<String> aud = token.getAudience();
        if (aud != null && aud.stream().anyMatch(accepted::contains)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token",
                "El claim aud " + aud + " no esta en la lista de audiencias aceptadas " + accepted,
                "https://tools.ietf.org/html/rfc6750#section-3.1"));
    }
}
