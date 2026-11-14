---
description: Coding standards for Shop v2.0
trigger: always_on
---

# Coding Standards

## Principles
SOLID, DRY, KISS, YAGNI, OWASP, DOP, FP, DDD.

## Clean Code
- Replace magic numbers with named constants
- Use meaningful names that reveal purpose
- Don't comment what the code does; explain why
- Each function does one thing; keep functions small
- DRY: extract repeated logic, single source of truth
- Hide implementation details; expose clear interfaces
- Write clear commit messages; small focused commits

## Java-Specific
- Target Java 25 and PaperMC 26.2 APIs
- Use Adventure `Component` for player-facing messages
- Preserve legacy `&` formatting only as intentional compatibility
- Treat placeholders as plain text, not MiniMessage input
- Use Folia-safe scheduling for world/block/sign operations
- Use thread-safe collections for shared mutable data
- Keep optional integrations optional; detect plugins at runtime
- Never commit credentials, tokens, production logs

## Testing
- Write tests before fixing bugs
- Test edge cases and error conditions
- 165-test baseline must pass
