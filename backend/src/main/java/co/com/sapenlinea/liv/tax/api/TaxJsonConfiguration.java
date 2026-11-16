package co.com.sapenlinea.liv.tax.api;

import co.com.sapenlinea.liv.tax.api.contract.model.CreateBlocked;
import co.com.sapenlinea.liv.tax.api.contract.model.CreateFiscalYearRequest;
import co.com.sapenlinea.liv.tax.api.contract.model.FiscalYearList;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * The JSON rules that the contract {@code tax.yaml} asks for and that Jackson does not apply by
 * itself, only for the DTO of the fiscal years (mixins over the generated classes, which are not
 * edited). Nothing else changes: the other endpoints and the events of the outbox keep the defaults
 * of Jackson.
 *
 * <ul>
 *   <li>{@code createBlocked} is optional: when there is no reason it is omitted, not written as
 *       {@code null}. So is its {@code minYear}: the contract allows it to be absent, but not
 *       {@code null}.
 *   <li>The {@code year} of the request is an integer: {@code "2026"}, {@code 2026.5} and {@code
 *       2026.0} are not turned into 2026 (they would answer 201, which the contract does not
 *       allow), and any other token is refused too.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class TaxJsonConfiguration {

  @Bean
  JsonMapperBuilderCustomizer taxJson() {
    return builder ->
        builder
            .addMixIn(FiscalYearList.class, OmitAbsentFields.class)
            .addMixIn(CreateBlocked.class, OmitAbsentFields.class)
            .addMixIn(CreateFiscalYearRequest.class, StrictYear.class);
  }

  /** Omits the optional fields that have no value ({@code createBlocked}, {@code minYear}). */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  abstract static class OmitAbsentFields {}

  /** The year of the request only takes a JSON integer. */
  abstract static class StrictYear {

    @JsonDeserialize(using = StrictIntegerDeserializer.class)
    abstract void setYear(Integer year);
  }

  /** Reads an integer only from a JSON integer: no text, no decimal, no boolean. */
  static final class StrictIntegerDeserializer extends ValueDeserializer<Integer> {

    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) {
      if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
        return (Integer) context.handleUnexpectedToken(Integer.class, parser);
      }
      return parser.getIntValue();
    }
  }
}
