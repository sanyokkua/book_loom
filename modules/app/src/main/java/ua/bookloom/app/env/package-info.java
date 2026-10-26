/**
 * Where a user's environment variables live outside the process: {@code launchctl} on macOS, the user and machine
 * environment in the Windows registry, and {@code environment.d} with {@code /etc/environment} on Linux. A packaged
 * app started from the Dock, a file manager or an IDE does not inherit a shell's {@code export} lines, so a key set
 * for the user account is read from here, at call time.
 */
@NullMarked
package ua.bookloom.app.env;

import org.jspecify.annotations.NullMarked;
