package notificacao_urgencia.service;

import notificacao_urgencia.domain.AvaliacaoCriticaMessage;
import notificacao_urgencia.exception.NotificacaoTransientException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;
import software.amazon.awssdk.services.ses.model.SesException;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SesEmailServiceTest {

    private static final AvaliacaoCriticaMessage AVALIACAO =
            new AvaliacaoCriticaMessage("Aula sem áudio", "CRITICA", "2026-11-08T14:32:01.169");

    private SendEmailRequest requisicao;

    private SesEmailService servico(Supplier<SendEmailResponse> resposta) {
        SesClient cliente = new SesClient() {
            @Override
            public SendEmailResponse sendEmail(SendEmailRequest request) {
                requisicao = request;
                return resposta.get();
            }

            @Override public String serviceName() { return "ses"; }
            @Override public void close() { }
        };
        return new SesEmailService(cliente, new EmailTemplateService(), "remetente@fiap.com", "admin@fiap.com");
    }

    @Test
    void enviaParaOAdministradorComOsDadosExigidos() {
        servico(() -> SendEmailResponse.builder().messageId("id").build()).enviar(AVALIACAO);

        assertEquals("remetente@fiap.com", requisicao.source());
        assertEquals(List.of("admin@fiap.com"), requisicao.destination().toAddresses());
        assertEquals("UTF-8", requisicao.message().body().text().charset());

        String corpo = requisicao.message().body().text().data();
        assertTrue(corpo.contains("Descrição: Aula sem áudio"));
        assertTrue(corpo.contains("Urgência: CRITICA"));
        // 14:32 em UTC equivale a 11:32 em São Paulo
        assertTrue(corpo.contains("Data de envio: 08/11/2026 às 11:32"));
    }

    @Test
    void converteFalhaDoSesEmExcecaoTransitoria() {
        SesEmailService servico = servico(() -> {
            throw SesException.builder().message("throttled").build();
        });

        assertThrows(NotificacaoTransientException.class, () -> servico.enviar(AVALIACAO));
    }
}
