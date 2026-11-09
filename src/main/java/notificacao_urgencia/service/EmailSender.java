package notificacao_urgencia.service;

import notificacao_urgencia.domain.AvaliacaoCriticaMessage;

public interface EmailSender {

    void enviar(AvaliacaoCriticaMessage avaliacao);
}
