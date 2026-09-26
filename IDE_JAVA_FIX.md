# IDE Java/Gradle fix

The old project could show `Unsupported class file major version 71` because an IDE was attempting to run an older Gradle/Groovy stack on a newer Java runtime.

This version does two things:

1. The bootstrap uses Gradle 9.8.0, which officially supports running Gradle on Java 27.
2. VS Code is configured to use the known Java 21 JDK for Gradle import and the Java language server.

The configured JDK is:

```text
C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot
```

If that folder changes, edit `.vscode/settings.json`.

After extracting the project, close and reopen VS Code so the Java extension reloads the settings.
