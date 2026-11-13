/**
 * Shared kernel: types used by more than one module (Money, Rate, FiscalYear, DaneCode...) and the
 * business errors {@link co.com.sapenlinea.liv.shared.RuleViolation} and {@link
 * co.com.sapenlinea.liv.shared.NotFound} (ADR 0035). Plain Java, no framework dependencies. It is a
 * normal module: a module that uses it needs its arrow drawn in doc. 01 §3.2, checked in {@code
 * ModularityTest} and approved by an ADR.
 */
package co.com.sapenlinea.liv.shared;
