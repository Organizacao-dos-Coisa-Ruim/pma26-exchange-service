package pma.exchange;

import java.math.BigDecimal;

/** Conversões entre o formato da API externa, a cotação interna (Rate) e o DTO de saída (QuoteOut). */
public final class QuoteParser {

  private QuoteParser() {
  }

  /** @throws NumberFormatException / NullPointerException se a API externa mandar valores inválidos */
  public static Rate toRate(AwesomeQuote quote) {
    return new Rate(new BigDecimal(quote.ask()), new BigDecimal(quote.bid()), quote.createDate());
  }

  public static QuoteOut toQuoteOut(Rate rate, String idAccount) {
    return QuoteOut.builder()
        .sell(rate.sell())
        .buy(rate.buy())
        .date(rate.date())
        .idAccount(idAccount)
        .build();
  }

}
