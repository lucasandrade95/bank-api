package com.lucasandrade.bankapi.auth;

import com.lucasandrade.bankapi.auth.dto.AuthResponse;
import com.lucasandrade.bankapi.auth.dto.LoginRequest;
import com.lucasandrade.bankapi.auth.dto.RegisterRequest;
import com.lucasandrade.bankapi.shared.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    /** Mesma mensagem para o duplicado detectado na checagem e para o pego pelo banco. */
    private static final String DUPLICATE_USERNAME_MESSAGE = "Username ja esta em uso";

    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(AppUserRepository repository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    /**
     * Cadastra um novo usuario (senha guardada como hash) e ja devolve um token.
     * Um username so pode ter um cadastro.
     *
     * <p>A checagem previa ({@code existsByUsername}) e um <b>check-then-act</b>: entre
     * o "ja existe?" e o INSERT ha uma janela em que outro cadastro com o mesmo
     * username pode inserir primeiro. Quem perde a corrida esbarra na restricao
     * UNIQUE da coluna — e o banco, nao a checagem, que garante a unicidade de fato.
     * Por isso a violacao e traduzida para a MESMA {@link BusinessException} do
     * caminho feliz: o cliente recebe 422 com a mesma mensagem, tenha ele perdido a
     * corrida ou nao. Sem essa traducao a excecao subia para o handler generico de
     * {@code DataIntegrityViolationException} e virava um 409 falando de
     * "requisicao ja em processamento" — resposta enganosa para um username repetido,
     * que manda o cliente esperar e repetir uma tentativa que nunca vai dar certo.
     * Mesmo tratamento ja aplicado ao documento duplicado em {@code AccountService.create}.
     *
     * <p>O {@code saveAndFlush} e necessario para o INSERT (e a violacao) acontecerem
     * aqui dentro do {@code try}, e nao la no commit da transacao, fora do alcance
     * deste catch.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (repository.existsByUsername(request.username())) {
            throw new BusinessException(DUPLICATE_USERNAME_MESSAGE);
        }
        AppUser user = new AppUser(request.username(), passwordEncoder.encode(request.password()));
        try {
            repository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(DUPLICATE_USERNAME_MESSAGE);
        }
        return tokenFor(user.getUsername());
    }

    /** Valida as credenciais e troca por um token JWT. Credencial invalida -> 401. */
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        return tokenFor(request.username());
    }

    private AuthResponse tokenFor(String username) {
        String token = jwtService.generateToken(username);
        return AuthResponse.bearer(token, jwtService.getExpirationSeconds());
    }
}
