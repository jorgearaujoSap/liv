/**
 * Shared kernel: types used by more than one module (Money, Rate, FiscalYear, DaneCode...) and the
 * business errors {@link co.com.sapenlinea.liv.shared.RuleViolation} and {@link
 * co.com.sapenlinea.liv.shared.NotFound} (ADR 0035). Plain Java, no framework dependencies. It is a
 * normal module: each module that uses it declares the arrow in doc. 01 §3.2 and {@code
 * ModularityTest}, with its ADR.
 */
package co.com.sapenlinea.liv.shared;
