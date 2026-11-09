package notificacao_urgencia.handler;

import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import notificacao_urgencia.domain.AvaliacaoCriticaMessage;
import notificacao_urgencia.exception.NotificacaoTransientException;
import notificacao_urgencia.service.NotificacaoService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificacaoUrgenciaHandlerTest {

    // Mesmo formato gerado pelo SqsService do tech4-ingestao-avaliacao
    private static final String MENSAGEM_DA_INGESTAO =
            "{\"descricao\": \"Aula sem áudio\", \"urgencia\": \"CRITICA\", \"dataEnvio\": \"2026-11-08T14:32:01.169\"}";

    private final List<AvaliacaoCriticaMessage> enviadas = new ArrayList<>();

    private NotificacaoUrgenciaHandler handler() {
        return new NotificacaoUrgenciaHandler(new NotificacaoService(enviadas::add));
    }

    @Test
    void enviaEmailParaMensagemPublicadaPelaIngestao() {
        SQSBatchResponse resposta = handler().handleRequest(evento(mensagem("1", MENSAGEM_DA_INGESTAO)), new TestContext());

        assertTrue(resposta.getBatchItemFailures().isEmpty());
        assertEquals(List.of(new AvaliacaoCriticaMessage("Aula sem áudio", "CRITICA", "2026-11-08T14:32:01.169")), enviadas);
    }

    @Test
    void reportaApenasAMensagemComJsonInvalido() {
        // Descrição com aspas sem escape, como o SqsService da ingestão gera hoje
        String jsonQuebrado = "{\"descricao\": \"O professor disse \"não sei\"\", \"urgencia\": \"CRITICA\", \"dataEnvio\": \"2026-11-08T14:32:01\"}";

        SQSBatchResponse resposta = handler().handleRequest(
                evento(mensagem("ok", MENSAGEM_DA_INGESTAO), mensagem("ruim", jsonQuebrado)), new TestContext());

        assertEquals(List.of("ruim"), idsComFalha(resposta));
        assertEquals(1, enviadas.size());
    }

    @Test
    void reportaMensagemSemCampoObrigatorio() {
        SQSBatchResponse resposta = handler().handleRequest(
                evento(mensagem("sem-descricao", "{\"urgencia\": \"CRITICA\", \"dataEnvio\": \"2026-11-08T14:32:01\"}")), new TestContext());

        assertEquals(List.of("sem-descricao"), idsComFalha(resposta));
        assertTrue(enviadas.isEmpty());
    }

    @Test
    void reportaFalhaDeEnvioParaASqsTentarNovamente() {
        NotificacaoUrgenciaHandler handler = new NotificacaoUrgenciaHandler(new NotificacaoService(avaliacao -> {
            throw new NotificacaoTransientException("SES indisponível");
        }));

        SQSBatchResponse resposta = handler.handleRequest(evento(mensagem("falhou", MENSAGEM_DA_INGESTAO)), new TestContext());

        assertEquals(List.of("falhou"), idsComFalha(resposta));
    }

    private static SQSEvent.SQSMessage mensagem(String id, String corpo) {
        SQSEvent.SQSMessage mensagem = new SQSEvent.SQSMessage();
        mensagem.setMessageId(id);
        mensagem.setBody(corpo);
        return mensagem;
    }

    private static SQSEvent evento(SQSEvent.SQSMessage... mensagens) {
        SQSEvent evento = new SQSEvent();
        evento.setRecords(List.of(mensagens));
        return evento;
    }

    private static List<String> idsComFalha(SQSBatchResponse resposta) {
        return resposta.getBatchItemFailures().stream()
                .map(SQSBatchResponse.BatchItemFailure::getItemIdentifier)
                .toList();
    }
}
