package notificacao_urgencia;

import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import notificacao_urgencia.handler.NotificacaoUrgenciaHandler;
import notificacao_urgencia.handler.TestContext;

import java.util.List;

public class LocalRunner {

    private static final String MENSAGEM_PADRAO =
            "{\"descricao\": \"A pior aula que já vi na vida\", \"urgencia\": \"CRITICA\", \"dataEnvio\": \"2026-11-08T14:32:01.169\"}";

    public static void main(String[] args) {
        // Aceita o corpo real de uma mensagem da fila como argumento
        String corpo = args.length > 0 ? args[0] : MENSAGEM_PADRAO;

        SQSEvent.SQSMessage mensagem = new SQSEvent.SQSMessage();
        mensagem.setMessageId("local-message-id");
        mensagem.setBody(corpo);

        SQSEvent evento = new SQSEvent();
        evento.setRecords(List.of(mensagem));

        SQSBatchResponse resposta = new NotificacaoUrgenciaHandler().handleRequest(evento, new TestContext());

        System.out.println("--- RESULTADO DO TESTE LOCAL ---");
        System.out.println("Mensagens com falha: " + resposta.getBatchItemFailures().size());
    }
}
