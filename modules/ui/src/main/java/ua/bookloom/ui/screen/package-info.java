/**
 * The screens: one FXML file and one controller each, built through the Guice controller factory.
 *
 * <p>A controller only wires nodes to a view model in {@code ua.bookloom.ui.state} and builds the few rows and chips
 * that depend on data; what a screen decides lives in the view model, and what it looks like lives in the stylesheet.
 */
@NullMarked
package ua.bookloom.ui.screen;

import org.jspecify.annotations.NullMarked;
