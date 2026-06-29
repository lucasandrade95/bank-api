package com.lucasandrade.bankapi.shared;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, List.of(ex.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, List.of("Credenciais invalidas"));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, List.of(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<String> messages = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, messages);
    }

    /**
     * Corpo da requisicao ausente, JSON malformado ou com tipo incompativel
     * (ex.: {@code amount: "abc"}). Sem este handler o Spring devolveria um corpo
     * de erro proprio, fora do padrao {@link ApiError} da API.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleNotReadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, List.of("Corpo da requisicao ausente ou malformado"));
    }

    /**
     * Parametro de caminho ou query com tipo invalido (ex.: id de conta que nao e
     * um UUID, ou {@code page=abc}). Mantem o mesmo corpo de erro padrao.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, List.of(ex.getName() + ": valor invalido"));
    }

    /**
     * Conflito de concorrencia: duas operacoes tentaram alterar a mesma conta a
     * partir do mesmo saldo (travamento otimista via {@code @Version}). A perdedora
     * recebe 409 e pode simplesmente repetir a requisicao com o saldo ja atualizado.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return build(HttpStatus.CONFLICT,
                List.of("Conta alterada concorrentemente, tente novamente"));
    }

    /** Violacoes em parametros de requisicao (ex.: @Min/@Max em page/size). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> messages = ex.getConstraintViolations().stream()
                .map(v -> lastNode(v.getPropertyPath().toString()) + ": " + v.getMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, messages);
    }

    /** Extrai so o nome do parametro (ex.: "statement.size" -> "size"). */
    private static String lastNode(String propertyPath) {
        int dot = propertyPath.lastIndexOf('.');
        return dot >= 0 ? propertyPath.substring(dot + 1) : propertyPath;
    }

    private ResponseEntity<ApiError> build(HttpStatus status, List<String> messages) {
        ApiError body = ApiError.of(status.value(), status.getReasonPhrase(), messages);
        return ResponseEntity.status(status).body(body);
    }
}
