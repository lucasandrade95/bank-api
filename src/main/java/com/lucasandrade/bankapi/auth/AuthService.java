package com.lucasandrade.bankapi.auth;

import com.lucasandrade.bankapi.auth.dto.AuthResponse;
import com.lucasandrade.bankapi.auth.dto.ChangePasswordRequest;
import com.lucasandrade.bankapi.auth.dto.LoginRequest;
import com.lucasandrade.bankapi.auth.dto.RegisterRequest;
import com.lucasandrade.bankapi.auth.dto.UserProfileResponse;
import com.lucasandrade.bankapi.shared.BusinessException;
import com.lucasandrade.bankapi.shared.NotFoundException;
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

    /**
     * Perfil do usuario autenticado. O username vem do token (subject), nunca da
     * URL — nao existe "perfil de outro usuario" nesta API. Somente leitura, por
     * isso {@code readOnly}: o Hibernate pula o dirty-check e o banco pode
     * otimizar a transacao.
     *
     * <p>Um token valido cujo usuario sumiu do banco ja e barrado no
     * {@code JwtAuthenticationFilter} (401), entao o {@code orElseThrow} aqui e a
     * rede de seguranca para o caso de a rota ser chamada com um principal que o
     * filtro nao produziu — mesma escolha do {@link #changePassword}.
     */
    @Transactional(readOnly = true)
    public UserProfileResponse profile(String username) {
        AppUser user = repository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("Usuario nao encontrado: " + username));
        return UserProfileResponse.from(user);
    }

    /**
     * Troca a senha do usuario autenticado, mediante a senha atual.
     *
     * <p>A senha atual e conferida com o {@code PasswordEncoder} (comparacao com o
     * hash guardado), nunca com o {@code AuthenticationManager}: aqui o usuario JA
     * esta autenticado pelo token, o que se pede e uma reautenticacao — e senha
     * atual errada e um <b>422</b> ({@link BusinessException}), nao 401. Um 401
     * diria ao cliente "seu token nao vale", e ele descartaria um token valido e
     * mandaria o usuario para o login; a mensagem certa e "o que voce digitou como
     * senha atual esta errado, tente de novo".
     *
     * <p>Nova senha igual a atual e recusada: "trocar" para a mesma senha nao troca
     * nada, e quem pede a troca (senha vazada, por exemplo) precisa que a antiga
     * deixe de valer.
     *
     * <p>Consequencia do JWT stateless que vale registrar: tokens ja emitidos
     * continuam validos ate expirarem ({@code security.jwt.expiration-minutes}) —
     * a troca invalida a senha antiga para novos logins, nao as sessoes em curso.
     * Invalidar tokens emitidos antes da troca pediria estado no servidor (ou um
     * {@code passwordChangedAt} conferido a cada requisicao), decisao separada.
     */
    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        AppUser user = repository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("Usuario nao encontrado: " + username));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException("Senha atual incorreta");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BusinessException("Nova senha deve ser diferente da senha atual");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        repository.save(user);
    }

    private AuthResponse tokenFor(String username) {
        String token = jwtService.generateToken(username);
        return AuthResponse.bearer(token, jwtService.getExpirationSeconds());
    }
}
