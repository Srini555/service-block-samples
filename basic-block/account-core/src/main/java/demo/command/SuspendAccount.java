package demo.command;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import demo.account.Account;
import demo.account.AccountEvent;
import demo.account.AccountEventType;
import demo.account.AccountStatus;
import demo.config.AwsLambdaConfig;
import demo.domain.LambdaResponse;
import demo.function.LambdaFunctionService;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Objects;

import static com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager.EXECUTION_TIMEOUT_ENABLED;
import static demo.account.AccountEventType.ACCOUNT_SUSPENDED;

@Service
public class SuspendAccount {

    private final Logger log = Logger.getLogger(SuspendAccount.class);
    private final LambdaFunctionService functionService;

    public SuspendAccount(AwsLambdaConfig.FunctionInvoker functionService) {
        this.functionService = functionService.getLambdaFunctionService();
    }

    @HystrixCommand(fallbackMethod = "accountSuspendedFallback", commandProperties = {
            @HystrixProperty(name = EXECUTION_TIMEOUT_ENABLED, value = "false")
    })
    public LambdaResponse<Account> apply(AccountEvent accountEvent) {
        try {
            return new LambdaResponse<>(functionService.accountSuspended(accountEvent));
        } catch (Exception ex) {
            if (Objects.equals(ex.getMessage(), "Account already suspended")) {
                return new LambdaResponse<>(ex, null);
            } else {
                log.error("Error invoking AWS Lambda function", ex);
                throw ex;
            }
        }
    }

    public LambdaResponse<Account> accountSuspendedFallback(AccountEvent event) {
        Account account = (Account) event.getPayload().get("account");
        List<AccountEvent> events = (List<AccountEvent>) event.getPayload().get("events");

        // Get the most recent event
        AccountEventType lastEvent = events.stream().findFirst()
                .map(AccountEvent::getType)
                .orElse(null);

        Assert.isTrue(lastEvent != ACCOUNT_SUSPENDED, "Account already suspended");

        account.setStatus(AccountStatus.valueOf(event.getType().toString()));

        return new LambdaResponse<>(null, account);
    }
}
