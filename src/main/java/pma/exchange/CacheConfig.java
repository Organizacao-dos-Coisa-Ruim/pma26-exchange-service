package pma.exchange;

import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Liga o cache do Spring (@Cacheable). O tipo (Redis), o TTL e o endereço vêm do application.yaml.
 *
 * O cache é uma otimização, não uma dependência obrigatória: se o Redis cair, o padrão do Spring
 * seria propagar o erro e o Exchange devolveria 500. Com o LoggingCacheErrorHandler, o erro do
 * Redis só gera um log e a chamada segue direto para a AwesomeAPI, como se não houvesse cache.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

  /** Cache das cotações por par (chave "USD-BRL"). */
  public static final String RATES = "rates";

  @Override
  public CacheErrorHandler errorHandler() {
    return new LoggingCacheErrorHandler();
  }

  /**
   * Faz o @Cacheable(sync = true) valer de verdade no Redis.
   *
   * O writer padrão do Redis não usa lock: com o cache vazio, 10 requisições simultâneas do mesmo
   * par fazem 10 chamadas à AwesomeAPI. O writer com lock guarda uma trava no próprio Redis: só uma
   * requisição busca a cotação e as outras esperam. Como a trava fica no Redis (e não na memória
   * da JVM), isso vale também entre vários pods do exchange quando o HPA escalar.
   */
  @Bean
  RedisCacheManagerBuilderCustomizer lockingCacheWriter(RedisConnectionFactory connectionFactory) {
    return builder -> builder.cacheWriter(RedisCacheWriter.lockingRedisCacheWriter(connectionFactory));
  }

}
