package cl.duoc.andesstay.reservations.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Declara los topicos Kafka que usa este microservicio. */
@Configuration
public class KafkaTopicsConfig {

    @Value("${reporteria.kafka.topic:reporteria-eventos}")
    private String reportTopic;

    @Value("${reservations.kafka.topic:reservations.events}")
    private String auditTopic;

    @Value("${reporteria.kafka.topic-replicas:1}")
    private int reportTopicReplicas;

    /** Topico de auditoria existente - mismo RF que hoy, sin romper audit. */
    @Bean
    public NewTopic reservationsEventsTopic() {
        return TopicBuilder.name(auditTopic)
                .partitions(1)
                .replicas(reportTopicReplicas)
                .build();
    }

    /** Topico de reporteria enriquecida - 3 particiones. */
    @Bean
    public NewTopic reporteriaEventosTopic() {
        return TopicBuilder.name(reportTopic)
                .partitions(3)
                .replicas(reportTopicReplicas)
                .build();
    }

    /** Dead Letter Topic para errores en el consumidor de reporteria. */
    @Bean
    public NewTopic reporteriaEventosDltTopic() {
        return TopicBuilder.name(reportTopic + ".DLT")
                .partitions(1)
                .replicas(reportTopicReplicas)
                .config(TopicConfig.RETENTION_MS_CONFIG, "604800000") // 7 dias
                .build();
    }
}
