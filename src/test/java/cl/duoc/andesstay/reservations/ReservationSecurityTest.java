package cl.duoc.andesstay.reservations;

import cl.duoc.andesstay.reservations.controller.ReservationController;
import cl.duoc.andesstay.reservations.controller.ReportController;
import cl.duoc.andesstay.reservations.controller.DemoController;
import cl.duoc.andesstay.reservations.admin.KafkaAdminController;
import cl.duoc.andesstay.reservations.service.ReservationService;
import cl.duoc.andesstay.reservations.repository.ReportKpiDailyRepository;
import cl.duoc.andesstay.reservations.repository.ReportEventLogRepository;
import cl.duoc.andesstay.reservations.admin.KafkaAdminService;
import cl.duoc.andesstay.reservations.producer.ReportEventProducer;
import cl.duoc.andesstay.reservations.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de seguridad por rol.
 * Verifica que:
 *  - Sin token -> 401
 *  - HUESPED -> 403 en /api/reportes/** y /api/admin/kafka/**
 *  - ADMIN -> 200 en /api/reportes/** y /api/admin/kafka/**
 *  - HUESPED -> puede acceder a /api/reservations/** (con restriccion por dueno en service)
 */
@WebMvcTest(controllers = {
    ReservationController.class,
    ReportController.class,
    KafkaAdminController.class,
    DemoController.class
})
@Import({
    cl.duoc.andesstay.reservations.config.SecurityConfig.class,
    cl.duoc.andesstay.reservations.config.CorsConfig.class,
    cl.duoc.andesstay.reservations.security.CognitoJwtAuthenticationConverter.class,
    cl.duoc.andesstay.reservations.security.RestAuthenticationEntryPoint.class,
    cl.duoc.andesstay.reservations.security.RestAccessDeniedHandler.class
})
class ReservationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private ReservationService reservationService;

    @MockBean
    private ReportKpiDailyRepository kpiRepo;

    @MockBean
    private ReportEventLogRepository eventLogRepo;

    @MockBean
    private KafkaAdminService kafkaAdminService;

    @MockBean
    private ReportEventProducer reportEventProducer;

    @MockBean
    private ReportService reportService;

    // ============ Tests de /api/reservations ============

    @Test
    void sinTokenDeberiaResponder401() throws Exception {
        mockMvc.perform(get("/api/reservations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenValidoConRolHuespedNoPuedeListarTodas() throws Exception {
        mockMvc.perform(get("/api/reservations")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_HUESPED"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenValidoConRolRecepcionistaPuedeListar() throws Exception {
        when(reservationService.buscar(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/reservations")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_RECEPCIONISTA"))))
                .andExpect(status().isOk());
    }

    @Test
    void crearReservaSinTokenDeberiaResponder401() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ============ Tests de /api/reportes — solo ADMIN ============

    @Test
    void huespedNoAccedeAReportes() throws Exception {
        mockMvc.perform(get("/api/reportes/kpi/resumen")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_HUESPED"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinTokenNoAccedeAReportes() throws Exception {
        mockMvc.perform(get("/api/reportes/kpi/resumen"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminAccedeAReportes() throws Exception {
        when(kpiRepo.sumReservasCreadas()).thenReturn(0L);
        when(kpiRepo.sumCanceladas()).thenReturn(0L);
        when(kpiRepo.sumConfirmadas()).thenReturn(0L);
        when(kpiRepo.sumNochesReservadas()).thenReturn(0L);
        when(kpiRepo.sumIngresosTotal()).thenReturn(java.math.BigDecimal.ZERO);
        when(kpiRepo.findAll()).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/reportes/kpi/resumen")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    // ============ Tests de /api/admin/kafka — solo ADMIN ============

    @Test
    void huespedNoAccedeAAdminKafka() throws Exception {
        mockMvc.perform(get("/api/admin/kafka/topics")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_HUESPED"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinTokenNoAccedeAAdminKafka() throws Exception {
        mockMvc.perform(get("/api/admin/kafka/topics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminAccedeAAdminKafka() throws Exception {
        when(kafkaAdminService.listarTopicos()).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/admin/kafka/topics")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }
}
