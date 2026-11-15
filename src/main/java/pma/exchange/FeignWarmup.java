package pma.exchange;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.cloud.openfeign.FeignClientFactory;
import org.springframework.cloud.openfeign.support.FeignHttpMessageConverters;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Contorna uma condição de corrida do Spring Cloud OpenFeign 5.0.2.
 *
 * O Feign monta a lista de conversores (JSON -> objeto) só na primeira chamada, e esse código
 * não é thread-safe: ele cria a lista vazia e depois a preenche. Se várias requisições chegam
 * juntas logo depois da subida (load test, pod novo do HPA), as que pegam a lista ainda vazia
 * falham com "'messageConverters' must not be empty", e o Exchange devolve 503.
 *
 * A correção é montar a lista na subida, antes de o Tomcat aceitar requisições:
 * afterSingletonsInstantiated roda depois de criar os beans e antes de o servidor web iniciar.
 */
@Component
@RequiredArgsConstructor
public class FeignWarmup implements SmartInitializingSingleton {

  /** Nome do @FeignClient da AwesomeAPI (cada cliente Feign tem o seu contexto e os seus conversores). */
  static final String AWESOME_API = "awesomeapi";

  private final FeignClientFactory feignClientFactory;

  @Override
  public void afterSingletonsInstantiated() {
    feignClientFactory.getInstance(AWESOME_API, FeignHttpMessageConverters.class).getConverters();
  }

}
