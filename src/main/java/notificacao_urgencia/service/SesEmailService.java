package notificacao_urgencia.service;

import notificacao_urgencia.domain.AvaliacaoCriticaMessage;
import notificacao_urgencia.exception.NotificacaoTransientException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

import java.net.URI;

public class SesEmailService implements EmailSender {

    private static final String CHARSET = "UTF-8";

    private final SesClient sesClient;
    private final EmailTemplateService emailTemplateService;
    private final String remetente;
    private final String destinatario;

    public SesEmailService() {
        this(criarCliente(), new EmailTemplateService(), obterVariavel("EMAIL_REMETENTE"), obterVariavel("ADMIN_EMAIL"));
    }

    SesEmailService(SesClient sesClient, EmailTemplateService emailTemplateService, String remetente, String destinatario) {
        this.sesClient = sesClient;
        this.emailTemplateService = emailTemplateService;
        this.remetente = remetente;
        this.destinatario = destinatario;
    }

    @Override
    public void enviar(AvaliacaoCriticaMessage avaliacao) {
        SendEmailRequest request = SendEmailRequest.builder()
                .source(remetente)
                .destination(Destination.builder().toAddresses(destinatario).build())
                .message(Message.builder()
                        .subject(conteudo(emailTemplateService.criarAssunto()))
                        .body(Body.builder().text(conteudo(emailTemplateService.criarCorpo(avaliacao))).build())
                        .build())
                .build();

        try {
            sesClient.sendEmail(request);
        } catch (SdkException e) {
            throw new NotificacaoTransientException("Falha ao enviar o e-mail pelo SES: " + e.getMessage(), e);
        }
    }

    private Content conteudo(String texto) {
        return Content.builder().data(texto).charset(CHARSET).build();
    }

    // Sem padrão: e-mails ausentes devem impedir a função de iniciar, em vez de falhar a cada mensagem
    private static String obterVariavel(String nome) {
        String valor = System.getenv(nome);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Variável de ambiente obrigatória não definida: " + nome);
        }
        return valor;
    }

    private static SesClient criarCliente() {
        String customEndpoint = System.getenv("AWS_ENDPOINT_URL");

        var builder = SesClient.builder()
                .region(Region.US_EAST_1);

        // Configuração caso esteja rodando localmente com LocalStack
        if (customEndpoint != null && !customEndpoint.isEmpty()) {
            builder.endpointOverride(URI.create(customEndpoint));
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("test", "test")
            ));
        }

        return builder.build();
    }
}
