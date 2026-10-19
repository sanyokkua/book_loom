/**
 * The book index: every word of a book read once into postings, so the name scans, the key-term scan, the name windows
 * and the term sharing questions are answered from one structure instead of re-reading the book per call.
 */
@NullMarked
package ua.bookloom.pipeline.index;

import org.jspecify.annotations.NullMarked;
