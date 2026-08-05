package com.lucasandrade.bankapi.account;

/**
 * Mascaramento do documento (CPF) do titular para exibicao.
 *
 * <p>O CPF entra na API uma vez, na criacao da conta, e e guardado inteiro — ele
 * e a chave de negocio do titular e a coluna UNIQUE que garante uma conta por
 * pessoa. O que ele <b>nao</b> precisa e voltar inteiro em toda resposta: quem ja
 * tem a conta nao aprende nada lendo o proprio CPF de volta, e a listagem
 * {@code GET /accounts} entregava o documento completo de <i>todos</i> os
 * titulares de uma vez. Dado pessoal que sai da API vira copia fora do nosso
 * controle — cache de cliente, log de proxy, corpo guardado numa
 * {@code Idempotency-Key} — entao ele so deve sair no minimo necessario.
 *
 * <p>O formato segue a convencao brasileira de exibicao parcial
 * ({@code ***.456.789-**}): escondem-se os tres primeiros digitos e os dois
 * verificadores, o suficiente para o titular reconhecer o proprio documento sem
 * que a resposta o transporte por inteiro. Nao e sigilo criptografico — os dois
 * verificadores sao deriváveis dos nove primeiros —, e sim <b>minimizacao</b>: a
 * API para de espalhar um identificador completo que ninguem precisa para operar.
 */
public final class Document {

    /** Quantidade de digitos de um CPF (a mesma do {@code @Pattern} e da coluna). */
    static final int CPF_LENGTH = 11;

    /**
     * Mascara para um valor fora do formato esperado. Uma funcao de mascaramento
     * que devolve o valor cru quando nao reconhece a entrada nao mascara nada —
     * o unico caso que importa e justamente o inesperado. Aqui o desconhecido
     * some por inteiro (<i>fail closed</i>).
     */
    static final String FULLY_MASKED = "***.***.***-**";

    private Document() {
    }

    /**
     * Devolve o documento em formato parcial ({@code ***.456.789-**}).
     *
     * @param document o CPF com 11 digitos; {@code null} continua {@code null}
     *                 (nao ha o que esconder), e qualquer outro formato vira
     *                 {@link #FULLY_MASKED}
     */
    public static String mask(String document) {
        if (document == null) {
            return null;
        }
        if (document.length() != CPF_LENGTH || !document.chars().allMatch(Character::isDigit)) {
            return FULLY_MASKED;
        }
        return "***." + document.substring(3, 6) + "." + document.substring(6, 9) + "-**";
    }
}
