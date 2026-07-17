package com.lucasandrade.bankapi.shared;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
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

    /**
     * A {@code Idempotency-Key} foi reenviada com uma requisicao diferente da que
     * ela ja atendeu. Nao e um retry, e reuso indevido da chave: devolver a resposta
     * guardada faria a nova operacao sumir sem erro. Volta 409 para o cliente usar
     * uma chave nova por operacao.
     */
    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiError> handleIdempotencyConflict(IdempotencyConflictException ex) {
        return build(HttpStatus.CONFLICT, List.of(ex.getMessage()));
    }

    /**
     * Rede de seguranca para violacao de restricao do banco: duas requisicoes
     * concorrentes colidiram numa coluna unica e a segunda gravacao foi recusada.
     * O caso conhecido e a mesma {@code Idempotency-Key} enviada em paralelo (a chave
     * e PRIMARY KEY); a operacao ja esta em andamento e nao deve ser refeita.
     *
     * <p>Este handler nao sabe QUAL restricao falhou, entao a mensagem e generica de
     * proposito — quem conhece a causa deve traduzi-la antes de chegar aqui, como faz
     * {@code AccountService.create} com o documento duplicado (422 com mensagem
     * especifica). Cravar uma causa aqui produziria respostas enganosas para as demais.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex) {
        return build(HttpStatus.CONFLICT,
                List.of("Conflito de concorrencia na gravacao, requisicao ja em processamento"));
    }

    /**
     * Argumento de requisicao logicamente invalido que nao da para expressar numa
     * unica anotacao Bean Validation — hoje o intervalo de datas do extrato com
     * {@code from} depois de {@code to}. Fica em 400 (entrada malformada), no mesmo
     * corpo padrao dos demais erros de parametro.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, List.of(ex.getMessage()));
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
