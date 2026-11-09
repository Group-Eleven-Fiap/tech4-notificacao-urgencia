package notificacao_urgencia.handler;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

public class TestContext implements Context {
    @Override public String getAwsRequestId() { return "local-id"; }
    @Override public String getLogGroupName() { return "local-log-group"; }
    @Override public String getLogStreamName() { return "local-log-stream"; }
    @Override public String getFunctionName() { return "notificacao-urgencia"; }
    @Override public String getFunctionVersion() { return "1"; }
    @Override public String getInvokedFunctionArn() { return "arn:local"; }
    @Override public ClientContext getClientContext() { return null; }
    @Override public CognitoIdentity getIdentity() { return null; }
    @Override public int getRemainingTimeInMillis() { return 15000; }
    @Override public int getMemoryLimitInMB() { return 512; }

    @Override
    public LambdaLogger getLogger() {
        return new LambdaLogger() {
            @Override public void log(String message) { System.out.println("[AWS LOG]: " + message); }
            @Override public void log(byte[] message) { System.out.println("[AWS LOG]: " + new String(message)); }
        };
    }
}
