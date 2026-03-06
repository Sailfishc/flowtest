# Product Context

## Why This Project Exists
The framework exists to make database-centric integration tests readable, deterministic, and cleanup-safe. Existing arrange-centric designs break down when data is only created during `act`, especially in sharded environments.

## Problems It Solves
- Snapshot/diff assertions without requiring pre-inserted arrange data
- Mixed scenarios where some tables are fixture-backed and others are watch-only
- Explicit route handling for sharded tables before baseline capture
- Reusable, business-friendly fixture composition via traits

## How It Should Feel
A scenario should read as business intent, not plumbing. Users declare fixtures only when needed, explicitly observe resources, attach route scope when required, execute the business action, and assert both result and data changes.

## User Experience Goals
- Low ceremony for common cases
- Fail fast on missing route or invalid fixture references
- Predictable cleanup behavior
- Example-driven onboarding through `flowtest-v2-cases`
