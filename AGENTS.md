This is a Minecraft plugin project built with Java and Maven.

## How to Build

To compile this project and create the plugin JAR file, you need to have Maven and a Java Development Kit (JDK) 17 or higher installed.

Run the following command from the root directory of the project:

```bash
mvn clean package
```

This will compile the source code, run any tests, and package the final artifact into the `target/` directory. The resulting JAR file (e.g., `DayNightWarfare-1.0-SNAPSHOT.jar`) can then be placed into the `plugins/` directory of a Spigot/Paper Minecraft server.
