package com.lucasandrade.bankapi.shared;

/**
 * Argumento de requisicao logicamente invalido que nao da para expressar numa
 * unica anotacao Bean Validation — hoje o intervalo de datas do extrato com
 * {@code from} depois de {@code to}. Mapeado para HTTP 400.
 *
 * <p>Existe para NAO usar {@link IllegalArgumentException} nesse papel. Uma
 * {@code IllegalArgumentException} e a excecao mais generica do Java: ela sobe de
 * qualquer biblioteca, do JDK ou de um bug nosso. Traduzir esse tipo inteiro para
 * 400 fazia a API dizer "sua requisicao esta malformada" para uma falha que podia
 * ser um defeito do servidor — e ainda devolvia a mensagem interna da excecao ao
 * cliente. Um tipo proprio marca a intencao: 400 so para quem foi lancado de
 * proposito como erro de entrada; o resto cai na rede de seguranca de 500.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
