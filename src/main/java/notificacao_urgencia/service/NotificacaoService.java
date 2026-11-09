package notificacao_urgencia.service;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import notificacao_urgencia.domain.AvaliacaoCriticaMessage;

public class NotificacaoService {

    private final EmailSender emailSender;

    public NotificacaoService() {
        this(new SesEmailService());
    }

    public NotificacaoService(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    public void processar(AvaliacaoCriticaMessage avaliacao, String messageId, LambdaLogger logger) {
        avaliacao.validar();

        logger.log(String.format("[INFO] notificacao.inicio messageId=%s urgencia=%s", messageId, avaliacao.urgencia()));

        emailSender.enviar(avaliacao);

        logger.log(String.format("[SUCESSO] notificacao.sucesso messageId=%s", messageId));
    }
}
