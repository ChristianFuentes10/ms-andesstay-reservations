package cl.duoc.andesstay.reservations;

import cl.duoc.andesstay.reservations.controller.ReservationController;
import cl.duoc.andesstay.reservations.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas basicas de la capa de seguridad, igual que CatalogProxySecurityTest
 * en el BFF:
 *  - sin token -> 401
 *  - token valido pero sin el rol requerido -> 403
 *  - token valido con rol ADMIN/RECEPCIONISTA -> pasa la autorizacion (200)
 */
@WebMvcTest(ReservationController.class)
class ReservationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private ReservationService reservationService;

    @Test
    void sinTokenDeberiaResponder401() throws Exception {
        mockMvc.perform(get("/api/reservations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenValidoConRolHuespedNoPuedeListarTodas() throws Exception {
        mockMvc.perform(get("/api/reservations")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_HUESPED"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenValidoConRolRecepcionistaPuedeListar() throws Exception {
        when(reservationService.buscar(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/reservations")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_RECEPCIONISTA"))))
                .andExpect(status().isOk());
    }

    @Test
    void crearReservaSinTokenDeberiaResponder401() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
