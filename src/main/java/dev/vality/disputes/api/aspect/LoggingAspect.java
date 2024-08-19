package dev.vality.disputes.api.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.CodeSignature;
import org.springframework.stereotype.Component;

@Aspect
@Slf4j
@Component
public class LoggingAspect {

    private static final String NAME_VALUE_DELIMITER = "=";
    private static final String PARAMETER_DELIMITER = ", ";

    @Pointcut("within(dev.vality.disputes.api.*) && within(*..*DelegateService)")
    public void isApiDelegateServiceClass() {
    }

    @Pointcut("execution(public * *(..))")
    public void isPublic() {
    }

    @Before("isApiDelegateServiceClass() && isPublic()")
    public void logMethod(JoinPoint joinPoint) {
        log.info("-> Req: {}, {}", getMethodName(joinPoint), getParameters(joinPoint));
    }

    private String getParameters(JoinPoint joinPoint) {
        CodeSignature signature = (CodeSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < parameterNames.length; i++) {
            stringBuilder
                    .append(parameterNames[i])
                    .append(NAME_VALUE_DELIMITER)
                    .append(joinPoint.getArgs()[i])
                    .append(PARAMETER_DELIMITER);
        }
        return stringBuilder.substring(0, stringBuilder.length() - PARAMETER_DELIMITER.length());
    }

    private String getMethodName(JoinPoint joinPoint) {
        return joinPoint.getSignature().getName();
    }
}
