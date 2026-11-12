package co.com.sapenlinea.liv.tax.golden;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What the assessment engine is given for a golden case: only what the functional document takes
 * for granted (category, origin of the base, adjustments). Amounts are exact decimals, read with
 * {@code decimalValue()}, never as {@code double}.
 *
 * <p>Exactly one of the four base forms is set, unless the case only asks for calendar dates; the
 * other three are {@code null}. The tax values (rates, ranges, rounding, calendar) are not here:
 * they come from the jurisdiction configuration.
 *
 * @param fiscalYear the fiscal year of the assessment
 * @param vehicleCategory the category, given and not derived (null when only dates are asked)
 * @param baseSourceType how the base is obtained ({@code OFFICIAL_TABLE} or {@code INVOICE}); null
 *     when the base is given already adjusted
 * @param taxableBase the base the document gives, already adjusted and without origin, in pesos
 * @param tableValue the table value, in pesos
 * @param tableValueThousands the table value as the ministry prints it, in thousands of pesos
 * @param invoiceValueWithoutVat the invoice value without VAT of a new vehicle, in pesos
 * @param baseAdjustments the adjustment codes, in the order the document lists them (may be empty)
 * @param newVehicle whether the vehicle is in its first year
 * @param accrualDate the accrual date of a new vehicle; null for the others, whose accrual the
 *     configuration gives
 */
record AssessmentGoldenInput(
    int fiscalYear,
    String vehicleCategory,
    String baseSourceType,
    BigDecimal taxableBase,
    BigDecimal tableValue,
    BigDecimal tableValueThousands,
    BigDecimal invoiceValueWithoutVat,
    List<String> baseAdjustments,
    boolean newVehicle,
    LocalDate accrualDate) {}
