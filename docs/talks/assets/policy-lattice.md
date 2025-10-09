# Policy Lattice - Slide Source (Export to PNG)

| Policy  | Missing Fields                        | Extra Fields | Type Mismatch | Field Order       | Name Case |
|---------|---------------------------------------|--------------|---------------|-------------------|-----------|
| Exact   | ❌ reject                             | ❌ reject    | ❌ reject     | As configured     | As is     |
| Backward| ⚠ allow if optional/defaulted (producer adds) | ✅ allow     | ❌ reject     | Flexible          | As is     |
| Forward | ✅ allow (consumer tolerates missing) | ❌ reject    | ❌ reject     | Flexible          | As is     |
| Full    | ✅ allow                              | ✅ allow     | ✅ allow      | Flexible          | Flexible  |

Modifiers
- Ordered: enforce index order
- CI (Case‑Insensitive): ignore case differences in names
- By‑Position: ignore names; types must match by index

Note: keep the table compact on the slide; big emoji marks work well on projectors.

