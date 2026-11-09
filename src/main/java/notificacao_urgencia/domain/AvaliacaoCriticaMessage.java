package notificacao_urgencia.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * Corpo da mensagem publicada na fila SQS pelo SqsService do tech4-ingestao-avaliacao.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AvaliacaoCriticaMessage(String descricao, String urgencia, String dataEnvio) {

    public void validar() {
        if (descricao == null || descricao.isBlank()) {
            throw new IllegalArgumentException("A descrição da avaliação é obrigatória.");
        }
        if (urgencia == null || urgencia.isBlank()) {
            throw new IllegalArgumentException("A urgência da avaliação é obrigatória.");
        }
        if (dataEnvio == null || dataEnvio.isBlank()) {
            throw new IllegalArgumentException("A data de envio da avaliação é obrigatória.");
        }
        try {
            LocalDateTime.parse(dataEnvio);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("A data de envio da avaliação é inválida: " + dataEnvio, e);
        }
    }
}
