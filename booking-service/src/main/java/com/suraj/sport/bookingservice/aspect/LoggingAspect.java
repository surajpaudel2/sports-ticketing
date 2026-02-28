package com.suraj.sport.bookingservice.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class LoggingAspect {

    // =====================================================================
    // POINTCUTS
    // =====================================================================

    /**
     * Pointcut targeting all methods in the service implementation layer.
     */
    @Pointcut("execution(* com.suraj.sport.bookingservice.service.impl.*.*(..))")
    public void serviceLayer() {}

    /**
     * Pointcut targeting all methods in the controller layer.
     */
    @Pointcut("execution(* com.suraj.sport.bookingservice.controller.*.*(..))")
    public void controllerLayer() {}

    /**
     * Combined pointcut targeting both service and controller layers.
     */
    @Pointcut("serviceLayer() || controllerLayer()")
    public void applicationLayer() {}

    // =====================================================================
    // ADVICE
    // =====================================================================

    /**
     * Around advice that logs method entry, exit, execution time and exceptions
     * for all methods in the application layer (controller + service).
     *
     * TODO: In the future, consider masking sensitive data in logs (e.g. user emails,
     * payment details) before logging method arguments for security compliance.
     *
     * TODO: Consider pushing logs to a centralized logging system (e.g. ELK Stack —
     * Elasticsearch, Logstash, Kibana) when Observability is implemented in Section 11.
     */
    @Around("applicationLayer()")
    public Object logMethodExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        Object[] args = joinPoint.getArgs();

        log.info("[START] {}.{}() called with args: {}", className, methodName, args);

        long startTime = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            log.info("[END] {}.{}() completed in {}ms", className, methodName, duration);
            return result;

        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[ERROR] {}.{}() failed after {}ms with exception: {}",
                    className, methodName, duration, ex.getMessage());
            throw ex;
        }
    }
}