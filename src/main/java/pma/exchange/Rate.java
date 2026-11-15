package pma.exchange;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Cotação de um par, igual para todos os usuários (não tem id-account).
 * É o valor que vai ser cacheado no Redis (issue #76), por isso é Serializable.
 */
public record Rate(BigDecimal sell, BigDecimal buy, String date) implements Serializable {
}
