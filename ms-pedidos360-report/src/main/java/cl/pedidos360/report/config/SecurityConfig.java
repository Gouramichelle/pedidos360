package cl.pedidos360.report.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Capa de validacion de borde del microservicio.
 *
 * En el caso original esta responsabilidad vivia en un BFF. Aqui se resuelve en
 * dos puntos: el JWT Authorizer del API Gateway valida el token antes de
 * enrutar, y este filtro lo vuelve a validar de forma independiente dentro del
 * servicio. La segunda validacion es la que importa para la seguridad real:
 * aunque alguien alcance el puerto del contenedor sin pasar por el Gateway,
 * sigue necesitando un token vigente emitido por el tenant de Azure AD.
 *
 * Lo que se valida aqui, punto por punto:
 *   - Firma          -> NimbusJwtDecoder contra el JWKS publicado por el issuer
 *   - Issuer         -> JwtValidators.createDefaultWithIssuer
 *   - Vigencia (exp) -> JwtTimestampValidator, incluido en el validador default
 *   - Audience       -> AudienceValidator
 *   - Autorizacion   -> @PreAuthorize por endpoint sobre los roles del claim
 *   - Errores        -> 401 sin token o token invalido, 403 sin el rol
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Rutas publicas: health checks del balanceador y la UI de OpenAPI. */
    private static final String[] PUBLIC_PATHS = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };

    private final String issuerUri;
    private final List<String> audiences;

    public SecurityConfig(
            @Value("${security.issuer-uri}") String issuerUri,
            @Value("${security.audiences}") List<String> audiences) {
        this.issuerUri = issuerUri;
        this.audiences = audiences;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> {
            })
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                .accessDeniedHandler(new BearerTokenAccessDeniedHandler()));
        return http.build();
    }

    /**
     * Decoder explicito en vez de las propiedades de Spring Boot, para dejar a
     * la vista que se valida firma, issuer, vigencia y audiencia.
     * withIssuerLocation resuelve el JWKS de forma perezosa, asi el servicio
     * levanta aunque el tenant todavia no este alcanzable.
     */
    @Bean
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
        OAuth2TokenValidator<Jwt> withIssuerAndExpiry = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withAudience = new AudienceValidator(audiences);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuerAndExpiry, withAudience));
        return decoder;
    }

    /**
     * Azure AD entrega los App Roles en el claim "roles" y los scopes delegados
     * en el claim "scp" separados por espacio. Spring Security no mapea ninguno
     * de los dos por defecto, asi que sin este converter hasRole() falla siempre.
     *
     * Se construye la instancia de JwtAuthenticationConverter directamente (no
     * como lambda expuesta como bean de tipo Converter) porque Spring Boot
     * escanea todos los beans Converter para registrarlos en el
     * ApplicationConversionService de MVC, y necesita resolver por reflection
     * sus tipos genericos <S, T>. Una lambda no conserva esa informacion y el
     * arranque falla con "Unable to determine source type <S> and target type
     * <T>"; la clase concreta de Spring Security si la conserva.
     *
     * principalClaimName fijo en "preferred_username": MSAL siempre pide el
     * scope "profile" junto con el nuestro, asi que Azure AD lo incluye en
     * todo token de usuario delegado. Si alguna vez faltara, el nombre del
     * principal quedaria en null (no rompe la autenticacion, solo el campo
     * "actor" en auditoria quedaria vacio para ese caso puntual).
     */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extraerAuthorities);
        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }

    private java.util.Collection<org.springframework.security.core.GrantedAuthority> extraerAuthorities(Jwt jwt) {
        java.util.List<org.springframework.security.core.GrantedAuthority> authorities = new java.util.ArrayList<>();

        java.util.List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null) {
            roles.forEach(r -> authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + r)));
        }

        String scp = jwt.getClaimAsString("scp");
        if (scp != null && !scp.isBlank()) {
            java.util.Arrays.stream(scp.split(" "))
                    .filter(s -> !s.isBlank())
                    .forEach(s -> authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_" + s)));
        }

        return authorities;
    }
}
