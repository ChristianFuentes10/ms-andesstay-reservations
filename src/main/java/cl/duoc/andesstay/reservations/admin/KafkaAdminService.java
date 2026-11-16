package cl.duoc.andesstay.reservations.admin;

import cl.duoc.andesstay.reservations.dto.*;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Servicio de administracion de Kafka: permite crear, modificar y eliminar
 * topicos y configuraciones. Usa AdminClient de la libreria kafka-clients.
 */
@Service
public class KafkaAdminService {

    private static final Logger log = LoggerFactory.getLogger(KafkaAdminService.class);

    /** Topicos protegidos que requieren force=true para eliminar. */
    private static final Set<String> TOPICOS_PROTEGIDOS = Set.of(
            "reservations.events", "reporteria-eventos");

    private final AdminClient adminClient;

    public KafkaAdminService(KafkaAdmin kafkaAdmin) {
        this.adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties());
    }

    /** Lista todos los topicos (excluye internos de Kafka). */
    public List<TopicInfoDto> listarTopicos() throws ExecutionException, InterruptedException {
        ListTopicsOptions opts = new ListTopicsOptions().listInternal(false);
        Set<String> nombres = adminClient.listTopics(opts).names().get();
        List<TopicInfoDto> resultado = new ArrayList<>();
        for (String nombre : nombres) {
            try {
                resultado.add(obtenerDetalleTopico(nombre));
            } catch (Exception e) {
                log.warn("No se pudo obtener detalle del topico {}: {}", nombre, e.getMessage());
            }
        }
        return resultado;
    }

    /** Obtiene el detalle de un topico especifico con sus configuraciones. */
    public TopicInfoDto obtenerDetalleTopico(String nombre) throws ExecutionException, InterruptedException {
        // Descripcion del topico
        DescribeTopicsResult desc = adminClient.describeTopics(List.of(nombre));
        TopicDescription td = desc.topicNameValues().get(nombre).get();
        int particiones = td.partitions().size();
        int replicas = td.partitions().get(0).replicas().size();

        // Configuraciones del topico
        ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, nombre);
        DescribeConfigsResult cfgResult = adminClient.describeConfigs(List.of(resource));
        Config config = cfgResult.all().get().get(resource);
        Map<String, String> configs = config.entries().stream()
                .filter(e -> !e.isDefault())
                .collect(Collectors.toMap(ConfigEntry::name, ConfigEntry::value));

        return new TopicInfoDto(nombre, particiones, replicas, td.isInternal(), configs);
    }

    /** Crea un nuevo topico con las particiones, replicas y configuraciones dadas. */
    public void crearTopico(CreateTopicRequest request, String actor)
            throws ExecutionException, InterruptedException {
        NewTopic nuevoTopico = new NewTopic(request.name(), request.partitions(),
                (short) request.replicationFactor());
        if (request.configs() != null && !request.configs().isEmpty()) {
            nuevoTopico.configs(request.configs());
        }
        adminClient.createTopics(List.of(nuevoTopico)).all().get();
        log.info("ADMIN [{}]: topico '{}' creado con {} particiones y RF={}",
                actor, request.name(), request.partitions(), request.replicationFactor());
    }

    /** Modifica configuraciones de un topico existente (incrementalAlterConfigs). */
    public void modificarConfiguracionTopico(String nombre, Map<String, String> configs, String actor)
            throws ExecutionException, InterruptedException {
        ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, nombre);
        List<AlterConfigOp> ops = configs.entrySet().stream()
                .map(e -> new AlterConfigOp(new ConfigEntry(e.getKey(), e.getValue()),
                        AlterConfigOp.OpType.SET))
                .collect(Collectors.toList());
        adminClient.incrementalAlterConfigs(Map.of(resource, ops)).all().get();
        log.info("ADMIN [{}]: configuraciones actualizadas en topico '{}': {}", actor, nombre, configs);
    }

    /** Aumenta el numero de particiones de un topico (Kafka no permite reducirlas). */
    public void aumentarParticiones(String nombre, int nuevasParticiones, String actor)
            throws ExecutionException, InterruptedException {
        // Verificar que sea mayor a las actuales
        TopicInfoDto actual = obtenerDetalleTopico(nombre);
        if (nuevasParticiones <= actual.particiones()) {
            throw new IllegalArgumentException(
                "Kafka no permite reducir particiones. Particiones actuales: "
                + actual.particiones() + ", solicitadas: " + nuevasParticiones);
        }
        adminClient.createPartitions(Map.of(nombre,
                NewPartitions.increaseTo(nuevasParticiones))).all().get();
        log.info("ADMIN [{}]: particiones de '{}' aumentadas a {}", actor, nombre, nuevasParticiones);
    }

    /** Elimina un topico. Los topicos protegidos requieren force=true. */
    public void eliminarTopico(String nombre, boolean force, String actor)
            throws ExecutionException, InterruptedException {
        if (TOPICOS_PROTEGIDOS.contains(nombre) && !force) {
            throw new IllegalArgumentException(
                "El topico '" + nombre + "' esta protegido. Use ?force=true para eliminarlo.");
        }
        adminClient.deleteTopics(List.of(nombre)).all().get();
        log.info("ADMIN [{}]: topico '{}' eliminado (force={})", actor, nombre, force);
    }

    /** Lista consumer groups con su lag total estimado. */
    public List<ConsumerGroupInfoDto> listarConsumerGroups()
            throws ExecutionException, InterruptedException {
        List<ConsumerGroupListing> grupos = new ArrayList<>(
                adminClient.listConsumerGroups().all().get());
        List<ConsumerGroupInfoDto> resultado = new ArrayList<>();
        for (ConsumerGroupListing grupo : grupos) {
            try {
                ConsumerGroupDescription desc = adminClient
                        .describeConsumerGroups(List.of(grupo.groupId()))
                        .all().get().get(grupo.groupId());

                // Calcular lag: diferencia entre offsets del grupo y offsets del final
                Map<TopicPartition, OffsetAndMetadata> offsets = adminClient
                        .listConsumerGroupOffsets(grupo.groupId())
                        .partitionsToOffsetAndMetadata().get();
                long lagTotal = 0;
                if (offsets != null && !offsets.isEmpty()) {
                    // Construir mapa de particion -> OffsetSpec.latest() con tipo explicito
                    Map<TopicPartition, OffsetSpec> offsetSpecs = new HashMap<>();
                    for (TopicPartition tp : offsets.keySet()) {
                        offsetSpecs.put(tp, OffsetSpec.latest());
                    }
                    Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsetsResult =
                            adminClient.listOffsets(offsetSpecs).all().get();

                    for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : offsets.entrySet()) {
                        ListOffsetsResult.ListOffsetsResultInfo endInfo = endOffsetsResult.get(entry.getKey());
                        if (endInfo != null) {
                            lagTotal += Math.max(0, endInfo.offset() - entry.getValue().offset());
                        }
                    }
                }
                resultado.add(new ConsumerGroupInfoDto(
                    grupo.groupId(),
                    desc.state().toString(),
                    lagTotal
                ));
            } catch (Exception e) {
                log.warn("No se pudo obtener detalle del consumer group {}: {}", grupo.groupId(), e.getMessage());
                resultado.add(new ConsumerGroupInfoDto(grupo.groupId(), "DESCONOCIDO", -1));
            }
        }
        return resultado;
    }
}
