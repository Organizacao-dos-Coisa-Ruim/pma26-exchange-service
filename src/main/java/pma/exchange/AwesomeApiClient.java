package pma.exchange;

import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Cliente Feign da AwesomeAPI (https://docs.awesomeapi.com.br/api-de-moedas): grátis e sem chave.
 *
 * GET /json/last/USD-BRL devolve um mapa com a chave "USDBRL":
 * {"USDBRL": {"code":"USD","codein":"BRL","bid":"5.6666","ask":"5.668","create_date":"2026-11-13 18:30:13", ...}}
 * Par inexistente: 404.
 *
 * Timeouts ficam no application.yaml (spring.cloud.openfeign.client.config.awesomeapi).
 */
@FeignClient(name = "awesomeapi", url = "${pma.exchange.api-url:https://economia.awesomeapi.com.br}")
public interface AwesomeApiClient {

  /** {@code pair} no formato "FROM-TO", ex.: "USD-BRL". */
  @GetMapping("/json/last/{pair}")
  Map<String, AwesomeQuote> last(@PathVariable("pair") String pair);

}
