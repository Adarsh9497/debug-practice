# Debug Practice

A collection of progressively harder Java and Spring Boot debugging exercises.
Each level contains a small, realistic codebase with one or more intentional defects.

## Run the exercises

Requirements: Java 21 and Maven 3.9+.

Run Level 1's JUnit tests:

```bash
cd level-1
mvn test
```

Compile and package the code:

```bash
mvn clean package
```

Level 1 is a service-only exercise with no application `main` method; its tests are the execution entry point.
