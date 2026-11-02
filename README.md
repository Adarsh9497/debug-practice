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

Run Level 3's Spring Boot API tests:

```bash
cd level-3
mvn test
```

Compile and package any level:

```bash
mvn clean package
```

Levels 1 and 2 are service-only exercises whose tests are the execution entry point. Level 3 is a Spring Boot application and can also be started with:

```bash
cd level-3
mvn spring-boot:run
```
