package com.grafanalab.orders;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entree.
 *
 * Notez `scanBasePackages` absent : on laisse le scan par defaut sur ce package,
 * mais comme AUCUNE classe metier n'est annotee, il ne trouve que les
 * @RestController et les @Configuration. Le service et les adaptateurs sont
 * instancies a la main dans BeanConfiguration.
 */
@SpringBootApplication
public class OrdersApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrdersApiApplication.class, args);
    }
}
