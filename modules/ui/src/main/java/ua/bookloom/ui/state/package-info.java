/**
 * The one bridge between engine threads and the scene graph: the observable state mirror screens bind to, and the
 * runner that drives a translation job and feeds the mirror.
 *
 * <p>Nothing here renders anything. Every mutation of an exposed property happens on the FX Application Thread
 * through a {@code publish*} method; everything that runs a job, waits or counts runs elsewhere.
 */
@NullMarked
package ua.bookloom.ui.state;

import org.jspecify.annotations.NullMarked;
