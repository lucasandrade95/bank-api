package com.lucasandrade.bankapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @EnableScheduling} liga as tarefas periodicas da aplicacao — hoje o
 * expurgo das {@code Idempotency-Key} vencidas
 * ({@code IdempotencyService.purgeExpired}).
 */
@SpringBootApplication
@EnableScheduling
public class BankApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankApiApplication.class, args);
    }
}
