package com.lucasandrade.bankapi.shared;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Janela de datas opcional usada pelos filtros de periodo (extrato e resumo do
 * extrato). Concentra num so lugar a validacao e a conversao que antes viviam
 * duplicadas em cada endpoint que aceita {@code from}/{@code to}.
 *
 * <p>As datas de entrada sao inclusivas nas duas pontas. Internamente a janela
 * vira um intervalo semi-aberto em UTC {@code [fromInstant, toInstant)}: o inicio
 * e a meia-noite de {@code from} e o fim e a meia-noite do dia SEGUINTE a
 * {@code to}, para o dia final entrar por completo. Qualquer ponta pode ser
 * {@code null} (filtro aberto daquele lado).
 *
 * <p>Um periodo invertido ({@code from} depois de {@code to}) e um pedido
 * logicamente impossivel: a fabrica lanca {@link InvalidRequestException}, que o
 * handler global traduz para 400 no corpo de erro padrao.
 */
public record DateRange(Instant fromInstant, Instant toInstant) {

    /**
     * Constroi a janela a partir das datas inclusivas {@code from}/{@code to}
     * (ambas anulaveis), validando a ordem e convertendo para o intervalo
     * semi-aberto em UTC.
     */
    public static DateRange of(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("from nao pode ser depois de to");
        }
        Instant fromInstant = from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to == null ? null : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new DateRange(fromInstant, toInstant);
    }
}
