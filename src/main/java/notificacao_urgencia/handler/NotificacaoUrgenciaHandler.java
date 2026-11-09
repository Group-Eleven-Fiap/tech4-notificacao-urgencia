package notificacao_urgencia.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import notificacao_urgencia.domain.AvaliacaoCriticaMessage;
import notificacao_urgencia.service.NotificacaoService;

import java.util.ArrayList;
import java.util.List;

public class NotificacaoUrgenciaHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NotificacaoService notificacaoService;

    public NotificacaoUrgenciaHandler() {
        this(new NotificacaoService());
    }

    NotificacaoUrgenciaHandler(NotificacaoService notificacaoService) {
        this.notificacaoService = notificacaoService;
    }

    @Override
    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        LambdaLogger logger = context.getLogger();
        List<SQSBatchResponse.BatchItemFailure> falhas = new ArrayList<>();

        for (SQSEvent.SQSMessage mensagem : event.getRecords()) {
            try {
                AvaliacaoCriticaMessage avaliacao = objectMapper.readValue(mensagem.getBody(), AvaliacaoCriticaMessage.class);
                notificacaoService.processar(avaliacao, mensagem.getMessageId(), logger);
            } catch (Exception e) {
                // Só a mensagem com erro volta para a fila; após as tentativas, a SQS move para a DLQ
                logger.log(String.format("[ERRO_NOTIFICACAO] notificacao.falha messageId=%s erro=%s",
                        mensagem.getMessageId(), e.getClass().getSimpleName()));
                falhas.add(SQSBatchResponse.BatchItemFailure.builder()
                        .withItemIdentifier(mensagem.getMessageId())
                        .build());
            }
        }

        return SQSBatchResponse.builder().withBatchItemFailures(falhas).build();
    }
}
