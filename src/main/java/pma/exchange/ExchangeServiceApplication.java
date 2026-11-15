package pma.exchange;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Registra só o cliente Feign da API externa de cotações.
 * O ExchangeController é implementado aqui (ExchangeResource), não consumido.
 */
@SpringBootApplication
@EnableFeignClients(clients = AwesomeApiClient.class)
public class ExchangeServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(ExchangeServiceApplication.class, args);
  }

}
