package com.lucasandrade.bankapi.account;

import com.lucasandrade.bankapi.shared.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aritmetica do limite diario de debito, sem subir o contexto Spring: a decisao
 * ("cabe no que sobrou de hoje?") e pura, entao vale testa-la direto, incluindo
 * as bordas que um teste de endpoint cobriria caro demais.
 */
class DailyDebitLimitTest {

    private final DailyDebitLimit limit = new DailyDebitLimit(new BigDecimal("1000.00"));

    @Test
    void allowsAmountBelowRemaining() {
        assertThatCode(() -> limit.ensureAllows(new BigDecimal("400.00"), new BigDecimal("100.00")))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsAmountExactlyAtRemaining() {
        // borda: gastar o limite ate o ultimo centavo e permitido
        assertThatCode(() -> limit.ensureAllows(new BigDecimal("900.00"), new BigDecimal("100.00")))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAmountOneCentOverRemaining() {
        // sobraram 100.00; 100.01 ja estoura, e a mensagem diz quanto ainda cabe
        assertThatThrownBy(() -> limit.ensureAllows(new BigDecimal("900.00"), new BigDecimal("100.01")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Limite diario de debito excedido; disponivel hoje: 100.00");
    }

    @Test
    void reportsZeroAvailable_whenLimitIsAlreadyExhausted() {
        assertThatThrownBy(() -> limit.ensureAllows(new BigDecimal("1000.00"), new BigDecimal("0.01")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("disponivel hoje: 0.00");
    }

    @Test
    void comparesMoneyByValue_notByScale() {
        // 1000 e 1000.00 sao o mesmo dinheiro: o limite compara valor, nao texto
        DailyDebitLimit unscaled = new DailyDebitLimit(new BigDecimal("1000"));
        assertThatCode(() -> unscaled.ensureAllows(BigDecimal.ZERO, new BigDecimal("1000.00")))
                .doesNotThrowAnyException();
        assertThat(unscaled.limit()).isEqualByComparingTo("1000.00");
    }

    @Test
    void remaining_reportsWhatIsLeftOfToday() {
        assertThat(limit.remaining(new BigDecimal("400.00"))).isEqualTo(new BigDecimal("600.00"));
        assertThat(limit.remaining(BigDecimal.ZERO)).isEqualTo(new BigDecimal("1000.00"));
    }

    @Test
    void remaining_neverGoesNegative() {
        // total do dia acima do teto (ex.: o limite configurado baixou depois de o
        // dinheiro ja ter saido): o disponivel e zero, nunca um valor negativo
        assertThat(limit.remaining(new BigDecimal("1500.00"))).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void remaining_comesInCanonicalMoneyScale() {
        // 100 (escala 0) subtraido de 1000.00 volta na escala monetaria de 2 casas
        assertThat(limit.remaining(new BigDecimal("100"))).isEqualTo(new BigDecimal("900.00"));
    }

    @Test
    void rejectsInvalidConfiguredLimit() {
        // limite mal configurado e erro de start da aplicacao, nao de requisicao
        assertThatThrownBy(() -> new DailyDebitLimit(new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyDebitLimit(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
