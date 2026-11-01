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

Run Level 2's JUnit tests:

```bash
cd level-2
mvn test
```

Compile and package either level:

```bash
mvn clean package
```

Levels 1 and 2 are service-only exercises with no application `main` method; their tests are the execution entry points.
