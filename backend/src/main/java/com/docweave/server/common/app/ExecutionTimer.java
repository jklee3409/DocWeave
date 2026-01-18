package com.docweave.server.common.app;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

@Slf4j
@Aspect
@Component
public class ExecutionTimer {

    @Around("execution(* com.docweave.server.doc.service.RagService.ask(..))" )
    public Object measureTime(ProceedingJoinPoint joinPoint) throws Throwable {
        log.info("⏱️ [Performance] '{}' 실행 시간 측정 시작", joinPoint.getSignature().getName());
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        Object result = joinPoint.proceed();

        stopWatch.stop();
        log.info("⏱️ [Performance] '{}' 실행 시간: {} ms", joinPoint.getSignature().getName(), stopWatch.getTotalTimeMillis());
        return result;
    }
}
