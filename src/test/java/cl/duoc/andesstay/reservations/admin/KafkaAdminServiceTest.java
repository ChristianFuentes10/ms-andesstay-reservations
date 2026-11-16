package cl.duoc.andesstay.reservations.admin;

import cl.duoc.andesstay.reservations.dto.CreateTopicRequest;
import cl.duoc.andesstay.reservations.dto.TopicInfoDto;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("nosec")
@EmbeddedKafka(partitions = 1, brokerProperties = {"listeners=PLAINTEXT://localhost:${random.int[20001,30000]}"})
@DirtiesContext
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.datasource.url=jdbc:h2:mem:testdb_admin;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "andesstay.services.catalog.base-url=http://localhost:9999"
})
class KafkaAdminServiceTest {

    @Autowired
    private KafkaAdmin kafkaAdmin;

    private KafkaAdminService kafkaAdminService;

    @BeforeEach
    void setUp() {
        kafkaAdminService = new KafkaAdminService(kafkaAdmin);
    }

    @Test
    void flujoCompleto_crear_modificarConfig_aumentarParticiones_y_eliminar() throws Exception {
        String topicName = "test-topic-" + UUID.randomUUID();

        // 1. Crear topico
        CreateTopicRequest createReq = new CreateTopicRequest(
            topicName,
            1,
            1,
            Map.of("retention.ms", "86400000")
        );
        kafkaAdminService.crearTopico(createReq, "test-admin");

        // 2. Detalle
        TopicInfoDto detalle = kafkaAdminService.obtenerDetalleTopico(topicName);
        assertThat(detalle.nombre()).isEqualTo(topicName);
        assertThat(detalle.particiones()).isEqualTo(1);

        // 3. Modificar config
        kafkaAdminService.modificarConfiguracionTopico(
            topicName,
            Map.of("retention.ms", "172800000"),
            "test-admin"
        );
        TopicInfoDto detalleMod = kafkaAdminService.obtenerDetalleTopico(topicName);
        assertThat(detalleMod.configuraciones()).containsEntry("retention.ms", "172800000");

        // 4. Aumentar particiones
        kafkaAdminService.aumentarParticiones(topicName, 2, "test-admin");
        TopicInfoDto detallePart = kafkaAdminService.obtenerDetalleTopico(topicName);
        assertThat(detallePart.particiones()).isEqualTo(2);

        // 5. Reducir particiones debe fallar
        assertThatThrownBy(() -> kafkaAdminService.aumentarParticiones(topicName, 1, "test-admin"))
            .isInstanceOf(IllegalArgumentException.class);

        // 6. Eliminar topico
        kafkaAdminService.eliminarTopico(topicName, false, "test-admin");
    }

    @Test
    void eliminarTopicoProtegido_sinForce_lanzaExcepcion() {
        assertThatThrownBy(() -> kafkaAdminService.eliminarTopico("reservations.events", false, "test-admin"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("protegido");
    }
}
