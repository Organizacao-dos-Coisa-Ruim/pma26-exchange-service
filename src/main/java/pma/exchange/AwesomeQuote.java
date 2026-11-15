package pma.exchange;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Formato da resposta da AwesomeAPI (só os campos que usamos).
 * Os valores vêm como texto ("5.6666"); o QuoteParser converte para BigDecimal.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AwesomeQuote(

    String code,
    String codein,
    /** Preço de compra. */
    String bid,
    /** Preço de venda. */
    String ask,
    @JsonProperty("create_date") String createDate

) {
}
