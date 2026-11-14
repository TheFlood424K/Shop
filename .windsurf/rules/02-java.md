---
description: Java-specific rules for Shop v2.0
trigger:
  - glob
globs:
  - "*.java"
---

# Java Rules (Shop v2.0)

## Stack
Java 25, Maven, Bukkit/Paper API 26.2, Adventure Components 5.2.0.

## Effective Java Principles
- Static factory methods over constructors where appropriate
- Builder for many constructor parameters
- Prefer dependency injection, composition over inheritance
- Minimize mutability; prefer immutable objects
- Minimize accessibility of classes and members
- Use interfaces to define types; prefer interfaces to abstract classes
- Use `@Override` consistently
- Prefer lambdas and method references to anonymous classes
- Check parameters for validity; make defensive copies
- Return empty collections, not nulls; use Optionals judiciously
- Use exceptions only for exceptional conditions
- Synchronize access to shared mutable data; document thread safety

## Shop-Specific
- No `org.spigotmc.*` imports; no NMS modules
- Adventure `Component` for all user-facing text (no Bungee chat)
- Folia-safe scheduling for region-sensitive operations
- Thread-safe collections for shared mutable state
- Optional integrations detected at runtime; handle absent plugins gracefully
