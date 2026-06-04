package com.bangku.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 *  ██████╗  █████╗ ███╗   ██╗ ██████╗ ██╗  ██╗██╗   ██╗
 *  ██╔══██╗██╔══██╗████╗  ██║██╔════╝ ██║ ██╔╝██║   ██║
 *  ██████╔╝███████║██╔██╗ ██║██║  ███╗█████╔╝ ██║   ██║
 *  ██╔══██╗██╔══██║██║╚██╗██║██║   ██║██╔═██╗ ██║   ██║
 *  ██████╔╝██║  ██║██║ ╚████║╚██████╔╝██║  ██╗╚██████╔╝
 *  ╚═════╝ ╚═╝  ╚═╝╚═╝  ╚═══╝ ╚═════╝ ╚═╝  ╚═╝ ╚═════╝
 *
 *  Account Microservice — Spring IoC Container Entry Point
 */
@SpringBootApplication
@EnableCaching
@EnableTransactionManagement
public class AccountServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}
