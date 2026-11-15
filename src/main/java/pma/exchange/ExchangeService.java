package pma.exchange;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Regras do Exchange: valida os códigos das moedas, busca a cotação (RateProvider)
 * e monta a resposta com a conta que pediu. Não tem banco.
 */
@Service
@RequiredArgsConstructor
public class ExchangeService {

  /** Código ISO 4217: 3 letras (USD, BRL, EUR...). */
  private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");

  private final RateProvider rateProvider;

  public QuoteOut quote(String from, String to, String idAccount) {
    String f = normalize(from);
    String t = normalize(to);
    if (f.equals(t)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "as moedas de origem e destino são iguais");
    }
    return QuoteParser.toQuoteOut(rateProvider.fetch(f, t), idAccount);
  }

  private static String normalize(String currency) {
    String code = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
    if (!CURRENCY.matcher(code).matches()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "código de moeda inválido: " + currency);
    }
    return code;
  }

}
