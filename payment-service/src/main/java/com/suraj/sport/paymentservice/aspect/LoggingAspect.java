package com.suraj.sport.paymentservice.aspect;

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

    @Pointcut("execution(* com.suraj.sport.paymentservice.service..*(..))")
    public void serviceLayer() {}

    @Pointcut("execution(* com.suraj.sport.paymentservice.controller..*(..))")
    public void controllerLayer() {}

    @Around("serviceLayer() || controllerLayer()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        log.info("ENTRY: {}", methodName);
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - start;
            log.info("EXIT: {} | Duration: {}ms", methodName, duration);
            return result;
        } catch (Exception ex) {
            log.error("EXCEPTION: {} | Message: {}", methodName, ex.getMessage());
            throw ex;
        }
    }
}