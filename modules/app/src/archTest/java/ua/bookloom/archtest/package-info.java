/**
 * The shared {@code arch-test} boundary suite: the eight ArchUnit rules of
 * {@code docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md#archunit-rules}, the violation fixtures
 * that prove each one bites, and the completeness test that proves none has been dropped.
 *
 * <p>Hosted in {@code :app} because {@code :app} is the only project whose classpath carries all eight modules at
 * once — {@code dependency-direction} cannot judge an edge it can only see one end of. This is a source set, not
 * a ninth module.
 */
@NullMarked
package ua.bookloom.archtest;

import org.jspecify.annotations.NullMarked;
