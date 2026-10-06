package cl.pedidos360.mqadmin.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cl.pedidos360.mqadmin.web.MqAdminDtos.CrearBindingRequest;
import cl.pedidos360.mqadmin.web.MqAdminDtos.CrearColaRequest;
import cl.pedidos360.mqadmin.web.MqAdminDtos.CrearExchangeRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * La pauta exige que la API no permita crear colas con nombres vacios ni
 * configuraciones invalidas. Estas pruebas ejercen esas reglas sobre los DTOs,
 * que es donde se aplican antes de que el controlador se ejecute.
 */
class MqAdminDtosTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void abrir() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void cerrar() {
        factory.close();
    }

    private <T> Set<ConstraintViolation<T>> validar(T dto) {
        return validator.validate(dto);
    }

    @Test
    void rechazaUnaColaConNombreVacio() {
        assertThat(validar(new CrearColaRequest("", null, null, null, null)))
                .anyMatch(v -> v.getMessage().contains("obligatorio"));
    }

    @Test
    void rechazaUnaColaConNombreSoloDeEspacios() {
        assertThat(validar(new CrearColaRequest("   ", null, null, null, null))).isNotEmpty();
    }

    @Test
    void rechazaNombresConEspaciosEnElMedio() {
        assertThat(validar(new CrearColaRequest("cola de pruebas", null, null, null, null))).isNotEmpty();
    }

    @Test
    void rechazaNombresQueSuperanLos255Caracteres() {
        assertThat(validar(new CrearColaRequest("q.".repeat(200), null, null, null, null))).isNotEmpty();
    }

    @Test
    void aceptaElFormatoDeNombreQueYaUsaElSistema() {
        assertThat(validar(new CrearColaRequest("q.cmd.email", null, null, null, null))).isEmpty();
    }

    @Test
    void unaColaNaceDurableSiNoSeIndicaLoContrario() {
        CrearColaRequest sinEspecificar = new CrearColaRequest("q.cmd.email", null, null, null, null);
        assertThat(sinEspecificar.durable()).isTrue();
        assertThat(sinEspecificar.autoDelete()).isFalse();
    }

    @Test
    void respetaDurableFalseCuandoSePideExplicitamente() {
        assertThat(new CrearColaRequest("q.efimera", false, true, null, null).durable()).isFalse();
    }

    @Test
    void rechazaUnExchangeSinTipo() {
        assertThat(validar(new CrearExchangeRequest("x.pruebas", "", null, null)))
                .anyMatch(v -> v.getMessage().contains("obligatorio"));
    }

    @Test
    void rechazaUnBindingSinColaNiExchange() {
        // Cada campo vacio viola dos reglas a la vez, @NotBlank y @Pattern, asi
        // que lo que importa no es cuantas violaciones hay sino que los dos
        // campos queden reportados.
        assertThat(validar(new CrearBindingRequest("", "", null)))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("cola", "exchange");
    }

    @Test
    void aceptaComodinesDeTopicEnLaRoutingKey() {
        assertThat(validar(new CrearBindingRequest("q.cmd.email", "cmd.topic", "email.*"))).isEmpty();
        assertThat(validar(new CrearBindingRequest("q.cmd.kitchen", "cmd.topic", "kitchen.#"))).isEmpty();
    }
}
