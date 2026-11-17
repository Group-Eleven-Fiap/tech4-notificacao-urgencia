# Notificação de Urgência

Serviço de notificação do Tech Challenge — Fase 4. Uma função AWS Lambda consome da fila SQS as avaliações críticas publicadas pelo [tech4-ingestao-avaliacao](https://github.com/Group-Eleven-Fiap/tech4-ingestao-avaliacao) e avisa o administrador por e-mail via Amazon SES.

## Modelo de cloud e arquitetura

A solução usa **serverless (FaaS) em nuvem pública AWS**: não há servidor para administrar, a escala é automática e o custo acompanha o uso, o que combina com o ambiente de créditos limitados.

```text
ingestao-avaliacao (Lambda)
  │ SendMessage (somente urgência CRITICA)
  ▼
SQS: filas-notificacoes-criticas ──(3 tentativas)──► SQS: filas-notificacoes-criticas-dlq
  │ a AWS invoca a função                                     │
  ▼                                                           ▼
NotificacaoUrgenciaHandler (Lambda)                  Alarme CloudWatch ──► SNS ──► e-mail
  │ desserializa e valida
  ▼
NotificacaoService ──► SesEmailService ──► Amazon SES ──► e-mail do administrador
```

| Componente | Motivo |
|------------|--------|
| AWS Lambda (Java 21, SnapStart) | Executa só quando há mensagem. |
| Amazon SQS + DLQ | Desacopla a ingestão, faz retry automático e preserva o que falhou. |
| Amazon SES | Envio de e-mail autenticado por IAM, sem senha no código. |
| CloudWatch + X-Ray | Logs, métricas, alarmes, dashboard e rastreamento. |
| AWS SAM + GitHub Actions | Infraestrutura como código e deploy automatizado. |

Este repositório faz parte de uma solução com três serviços, implantados nesta ordem:

| Ordem | Repositório | Responsabilidade |
|-------|-------------|------------------|
| 1 | **tech4-notificacao-urgencia** (este) | Criar a fila e enviar o e-mail de aviso quando a avaliação é crítica. |
| 2 | [tech4-ingestao-avaliacao](https://github.com/Group-Eleven-Fiap/tech4-ingestao-avaliacao) | Receber, validar, classificar e gravar a avaliação; publicar na fila as críticas. |
| 3 | [tech4-relatorio-semanal](https://github.com/Group-Eleven-Fiap/tech4-relatorio-semanal) | Enviar o relatório semanal com média e quantidades. |

## Documentação da função

**Responsabilidade única:** transformar uma mensagem de avaliação crítica em um e-mail ao administrador. Não grava dados, não calcula urgência e não gera relatório.

| Classe | Responsabilidade |
|--------|------------------|
| `handler.NotificacaoUrgenciaHandler` | Entrada da Lambda; lê o lote e informa quais mensagens falharam. |
| `service.NotificacaoService` | Valida a avaliação, registra logs e aciona o envio. |
| `service.EmailSender` / `service.SesEmailService` | Contrato de envio e implementação com SES. |
| `service.EmailTemplateService` | Monta assunto e corpo do e-mail. |
| `domain.AvaliacaoCriticaMessage` | Modelo e validação da mensagem. |

Ponto de entrada: `notificacao_urgencia.handler.NotificacaoUrgenciaHandler::handleRequest`

### Mensagem consumida

Formato publicado pelo `SqsService` da ingestão:

```json
{
  "descricao": "O atendimento demorou mais do que o esperado.",
  "urgencia": "CRITICA",
  "dataEnvio": "2026-11-08T14:32:01.169"
}
```

`dataEnvio` é tratado como UTC, que é o relógio da Lambda de ingestão, e exibido no horário de Brasília. Campos adicionais são ignorados.

### E-mail enviado

```text
Assunto: [URGENTE] Avaliação crítica recebida

Uma avaliação crítica foi recebida na plataforma de feedback.

Descrição: O atendimento demorou mais do que o esperado.
Urgência: CRITICA
Data de envio: 08/11/2026 às 11:32
```

### Tratamento de falhas

A função devolve à SQS apenas as mensagens com erro (`ReportBatchItemFailures`). Cada mensagem tem até 3 tentativas e depois vai para a DLQ, que dispara um alarme. A entrega da SQS é "pelo menos uma vez", então um e-mail duplicado é possível em caso de reprocessamento.

## Integração com a ingestão

Este repositório cria a fila, portanto deve ser implantado primeiro. O `template.yaml` do [tech4-ingestao-avaliacao](https://github.com/Group-Eleven-Fiap/tech4-ingestao-avaliacao) importa os dois valores exportados aqui:

1. `SQS_QUEUE_URL` da Lambda `ingestao-avaliacao` recebe `!ImportValue tech4-filas-notificacoes-criticas-url` (output `FilaNotificacoesCriticasUrl`). Sem essa variável, a ingestão não inicia.
2. A role da função recebe `sqs:SendMessage` no ARN exportado como `tech4-filas-notificacoes-criticas-arn`.
3. Os dois stacks devem estar na mesma conta e na região `us-east-1`.

Se a ingestão for implantada antes deste stack, o deploy falha porque os exports ainda não existem.

## Configuração

| Variável | Uso | Obrigatória |
|----------|-----|-------------|
| `ADMIN_EMAIL` | Destinatário do e-mail. | Sim |
| `EMAIL_REMETENTE` | Remetente, verificado no SES. | Sim |
| `AWS_ENDPOINT_URL` | Endpoint alternativo para testes locais. Não é definido na AWS. | Não |

## Segurança e governança

- Role IAM exclusiva da função: consumir somente esta fila, enviar e-mail somente pelas identidades configuradas e escrever somente no próprio log group.
- Filas criptografadas (SSE gerenciada pela SQS) e política que nega acesso sem TLS.
- Deploy via OIDC: nenhuma chave da AWS fica guardada no GitHub.
- Nenhuma senha no código: o SES é autorizado pela role da função.
- O corpo do e-mail não é registrado em log.

## Monitoramento

| Recurso | O que mostra |
|---------|--------------|
| Logs `/aws/lambda/notificacao-urgencia` | `notificacao.inicio`, `notificacao.sucesso`, `notificacao.falha`, com retenção de 14 dias. |
| Alarme `notificacao-urgencia-falhas` | Mensagem que falhou, inclusive em falha parcial de lote. |
| Alarme `notificacao-urgencia-erros-lambda` | Execução encerrada com erro. |
| Alarme `notificacao-urgencia-dlq` | Mensagem que esgotou as tentativas. |
| Dashboard `notificacao-urgencia` | Invocações, erros, duração, fila e DLQ. |
| AWS X-Ray | Rastreamento de cada execução. |

Os alarmes avisam o `AdminEmail` pelo tópico SNS `notificacao-urgencia-alarmes`. A AWS envia um e-mail de confirmação da inscrição, que precisa ser aceito.

## Deploy

Pré-requisitos, feitos uma única vez na conta AWS:

1. Verificar `EMAIL_REMETENTE` e `ADMIN_EMAIL` no SES, região `us-east-1`. A conta começa em sandbox, e sem isso o envio é recusado.
2. Criar a role de deploy:

   ```powershell
   aws cloudformation deploy --template-file infra/github-oidc.yaml --stack-name tech4-github-oidc --capabilities CAPABILITY_NAMED_IAM --region us-east-1
   ```

3. No GitHub, em *Settings → Secrets and variables → Actions → Variables*, criar `AWS_ROLE_ARN` (output `RoleDeployArn`), `ADMIN_EMAIL` e `EMAIL_REMETENTE`.

Depois disso, cada push na `main` roda os testes e, se passarem, executa `sam build` e `sam deploy`. Enquanto `AWS_ROLE_ARN` não existir, o pipeline roda só os testes.

Deploy manual:

```powershell
sam build
sam deploy --stack-name tech4-notificacao-urgencia --region us-east-1 --resolve-s3 --capabilities CAPABILITY_IAM --parameter-overrides AdminEmail=admin@exemplo.com EmailRemetente=remetente@exemplo.com
```

## Build e testes

Pré-requisito: JDK 21.

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

São 6 testes unitários: mensagem válida da ingestão, mensagem com JSON inválido ou sem campo obrigatório (só ela é devolvida à fila), falha de envio (a SQS tenta de novo) e envio pelo SES com descrição, urgência e data. Nenhum deles acessa a AWS. Eles rodam no pipeline antes de cada deploy.

## Verificação após o deploy

```powershell
aws cloudformation describe-stacks --stack-name tech4-notificacao-urgencia --region us-east-1 --query "Stacks[0].StackStatus"
aws logs tail /aws/lambda/notificacao-urgencia --region us-east-1 --since 15m
aws sqs get-queue-attributes --queue-url <FilaDlqUrl> --attribute-names ApproximateNumberOfMessages --region us-east-1
```

Para testar o fluxo completo, envie uma avaliação com nota de 0 a 4 pela API do [tech4-ingestao-avaliacao](https://github.com/Group-Eleven-Fiap/tech4-ingestao-avaliacao). O log deve mostrar `notificacao.inicio` e `notificacao.sucesso`, o e-mail chega ao administrador e a DLQ continua vazia (o valor `FilaDlqUrl` é um output do stack).

## Execução local

O `LocalRunner` chama o handler com um evento SQS simulado, sem implantar nada. Ele usa a AWS de verdade, então **envia um e-mail real pelo SES**. Para isso, é preciso ter credenciais da AWS na máquina (por exemplo, `aws login`) e os dois endereços verificados no SES, em `us-east-1`:

```powershell
$env:AWS_REGION = "us-east-1"
$env:EMAIL_REMETENTE = "remetente@exemplo.com"
$env:ADMIN_EMAIL = "admin@exemplo.com"
```

Depois, execute `notificacao_urgencia.LocalRunner` pela IDE. Para usar o corpo de uma mensagem real da fila, passe-o como argumento.
