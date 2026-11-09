package notificacao_urgencia.service;

import notificacao_urgencia.domain.AvaliacaoCriticaMessage;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class EmailTemplateService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    private static final ZoneId FUSO_BRASIL = ZoneId.of("America/Sao_Paulo");

    public String criarAssunto() {
        return "[URGENTE] Avaliação crítica recebida";
    }

    public String criarCorpo(AvaliacaoCriticaMessage avaliacao) {
        return String.format(
                "Uma avaliação crítica foi recebida na plataforma de feedback.%n%n"
                        + "Descrição: %s%n"
                        + "Urgência: %s%n"
                        + "Data de envio: %s%n",
                avaliacao.descricao(), avaliacao.urgencia(), formatarData(avaliacao.dataEnvio()));
    }

    // A ingestão gera dataEnvio com LocalDateTime.now(), que na Lambda está em UTC
    private String formatarData(String dataEnvio) {
        return LocalDateTime.parse(dataEnvio)
                .atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(FUSO_BRASIL)
                .format(FORMATTER);
    }
}
