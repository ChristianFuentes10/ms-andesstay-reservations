package cl.duoc.andesstay.reservations.admin;

import cl.duoc.andesstay.reservations.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Endpoints de administracion de Kafka (solo ADMIN).
 * Permite crear, modificar y eliminar topicos y configuraciones.
 */
@RestController
@RequestMapping("/api/admin/kafka")
@PreAuthorize("hasRole('ADMIN')")
public class KafkaAdminController {

    private final KafkaAdminService kafkaAdminService;

    public KafkaAdminController(KafkaAdminService kafkaAdminService) {
        this.kafkaAdminService = kafkaAdminService;
    }

    /** Lista todos los topicos (excluye internos). */
    @GetMapping("/topics")
    public ResponseEntity<List<TopicInfoDto>> listarTopicos() throws ExecutionException, InterruptedException {
        return ResponseEntity.ok(kafkaAdminService.listarTopicos());
    }

    /** Detalle de un topico: particiones, lideres, replicas, ISR y configuraciones. */
    @GetMapping("/topics/{nombre}")
    public ResponseEntity<TopicInfoDto> detalleTopico(@PathVariable String nombre)
            throws ExecutionException, InterruptedException {
        return ResponseEntity.ok(kafkaAdminService.obtenerDetalleTopico(nombre));
    }

    /** Crea un nuevo topico. Valida nombre, particiones y factor de replicacion. */
    @PostMapping("/topics")
    public ResponseEntity<Map<String, String>> crearTopico(
            @Valid @RequestBody CreateTopicRequest request,
            @AuthenticationPrincipal Jwt jwt) throws ExecutionException, InterruptedException {
        kafkaAdminService.crearTopico(request, jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("mensaje", "Topico '" + request.name() + "' creado exitosamente"));
    }

    /** Modifica configuraciones de un topico (ej: retention.ms, cleanup.policy). */
    @PutMapping("/topics/{nombre}/config")
    public ResponseEntity<Map<String, String>> modificarConfig(
            @PathVariable String nombre,
            @Valid @RequestBody UpdateTopicConfigRequest request,
            @AuthenticationPrincipal Jwt jwt) throws ExecutionException, InterruptedException {
        kafkaAdminService.modificarConfiguracionTopico(nombre, request.configs(), jwt.getSubject());
        return ResponseEntity.ok(Map.of("mensaje", "Configuracion actualizada en topico '" + nombre + "'"));
    }

    /** Aumenta particiones de un topico (Kafka no permite reducirlas). */
    @PutMapping("/topics/{nombre}/partitions")
    public ResponseEntity<Map<String, String>> aumentarParticiones(
            @PathVariable String nombre,
            @RequestParam int cantidad,
            @AuthenticationPrincipal Jwt jwt) throws ExecutionException, InterruptedException {
        kafkaAdminService.aumentarParticiones(nombre, cantidad, jwt.getSubject());
        return ResponseEntity.ok(Map.of("mensaje", "Particiones de '" + nombre + "' actualizadas a " + cantidad));
    }

    /** Elimina un topico. Los topicos del sistema requieren force=true. */
    @DeleteMapping("/topics/{nombre}")
    public ResponseEntity<Map<String, String>> eliminarTopico(
            @PathVariable String nombre,
            @RequestParam(defaultValue = "false") boolean force,
            @AuthenticationPrincipal Jwt jwt) throws ExecutionException, InterruptedException {
        kafkaAdminService.eliminarTopico(nombre, force, jwt.getSubject());
        return ResponseEntity.ok(Map.of("mensaje", "Topico '" + nombre + "' eliminado"));
    }

    /** Lista consumer groups con su estado y lag. */
    @GetMapping("/consumer-groups")
    public ResponseEntity<List<ConsumerGroupInfoDto>> listarConsumerGroups()
            throws ExecutionException, InterruptedException {
        return ResponseEntity.ok(kafkaAdminService.listarConsumerGroups());
    }
}
