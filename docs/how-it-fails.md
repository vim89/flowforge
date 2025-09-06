# How it fails (by policy)
- **Exact**: shapes must match exactly (same fields & types).
- **ExactUnordered**: same as Exact, order ignored.
- **Backward**: new fields allowed only if `Option[_]` or a default is present.
- **Forward**: extra fields in the output are allowed; missing fields fail.
- **Full**: permissive (no shape failures).
