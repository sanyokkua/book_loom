# Provider keys

*How to give BookLoom an API key without ever typing or storing it.*

A provider that needs a key (LM Studio with a key, OpenAI, a company gateway) stores only the **name** of an
environment variable in Settings › Providers › Key variable; the key itself is never typed into BookLoom or saved.
BookLoom reads the variable when it sends a request: first from its own environment, then from the place your
operating system keeps your user account's variables. An app opened from the Dock, the Start menu, a file manager or
an IDE does **not** see `export` lines in `~/.zshrc` or `~/.bashrc`, so set the variable in one of the places below.
BookLoom picks a new value up within two seconds — no restart is needed. The form shows the variable as "set" once
BookLoom can read it.

**macOS** — for the current login session:

```bash
launchctl setenv LMS_KEY your-key-value
```

To keep it after a reboot, create `~/Library/LaunchAgents/ua.bookloom.envvars.plist`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>ua.bookloom.envvars</string>
  <key>ProgramArguments</key>
  <array>
    <string>launchctl</string>
    <string>setenv</string>
    <string>LMS_KEY</string>
    <string>your-key-value</string>
  </array>
  <key>RunAtLoad</key>
  <true/>
</dict>
</plist>
```

and load it once with `launchctl load ~/Library/LaunchAgents/ua.bookloom.envvars.plist`. (`launchctl unsetenv
LMS_KEY` removes the session value.)

**Windows** — for your user account (System Properties › Advanced › Environment Variables › User variables is the
same place):

```bat
setx LMS_KEY your-key-value
```

BookLoom also reads the machine-wide variables an administrator set.

**Linux** — create `~/.config/environment.d/50-bookloom.conf` (a later file in that folder wins) holding

```
LMS_KEY=your-key-value
```

or add the same line to `/etc/environment` for every user. Quotes around the value are allowed.

A key variable written as `$LMS_KEY` or `${env:LMS_KEY}` is refused with a hint: the field takes the bare name. In a
custom header value, `${env:LMS_KEY}` is the form that names a variable.
