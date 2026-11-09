package notificacao_urgencia.exception;

public class NotificacaoTransientException extends RuntimeException {

    public NotificacaoTransientException(String message) {
        super(message);
    }

    public NotificacaoTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
