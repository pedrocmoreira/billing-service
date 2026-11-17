package com.pedrocmoreira.garage.billing.presentation.controller;

import com.pedrocmoreira.garage.billing.domain.exception.BusinessException;
import com.pedrocmoreira.garage.billing.domain.exception.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail notFound(EntityNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Entidade não encontrada", ex.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail business(BusinessException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Regra de negócio violada", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Requisição inválida", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail generic(Exception ex) {
        log.error("Erro inesperado", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno", "Erro interno inesperado");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
