package com.burgerlab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BurgerOrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(BurgerOrderApplication.class, args);
    }
}
