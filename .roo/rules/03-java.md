# Java Guidelines (Shop v2.0)

## Principles
SOLID, DRY, KISS, YAGNI, OWASP, DOP, FP, DDD.

## Java 25 + PaperMC 26.2
- Target Java 25 and PaperMC 26.2 APIs
- Adventure `Component` for player-facing messages (no Bungee chat)
- Folia-safe scheduling for world/block/sign operations
- Thread-safe collections for shared mutable data
- Optional integrations detected at runtime; handle absent plugins

## Effective Java
- Minimize mutability; prefer immutable objects
- Minimize accessibility; composition over inheritance
- Use `@Override` consistently; prefer lambdas/method references
- Check parameters; defensive copies; return empty collections not nulls
- Exceptions only for exceptional conditions; document thread safety
