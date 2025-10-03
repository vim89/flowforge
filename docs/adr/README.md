# Architecture Decision Records (ADRs)

This directory contains **immutable architectural decisions** for FlowForge. ADRs document the context, decision, and consequences of significant technical choices.

## 📖 What is an ADR?

An Architecture Decision Record captures:
- **Context**: The forces at play (technical, organizational, project)
- **Decision**: The change we're proposing or have made
- **Consequences**: The resulting context after applying the decision

## 🎯 Purpose

ADRs serve as:
- Historical record of why decisions were made
- Onboarding material for new team members
- Reference for future architectural discussions
- Justification for current system design

## 📋 Index

See [INDEX.md](INDEX.md) for the complete, organized list of all ADRs with their mappings to archived sources.

## 📝 Creating a New ADR

1. **Copy the template**: `cp 000-template.md NNN-your-decision.md`
2. **Fill in all sections**: Context, Decision, Consequences, Alternatives
3. **Number sequentially**: Use next available number (check INDEX.md)
4. **Include examples**: Add code samples where relevant
5. **Link related docs**: Reference other ADRs, evidence, plans
6. **Update INDEX.md**: Add entry to the index

## ✅ ADR Lifecycle

```
Proposed → Under Review → Accepted → Implemented → Superseded (if needed)
```

- **Proposed**: Draft ADR open for discussion
- **Under Review**: Team is evaluating the decision
- **Accepted**: Decision approved and documented
- **Implemented**: Decision applied to codebase
- **Superseded**: Replaced by newer ADR (original preserved for history)

## 📐 ADR Template Structure

Use [000-template.md](000-template.md) which includes:

1. **Title**: Short, descriptive name
2. **Status**: Proposed | Accepted | Superseded by ADR-XXX
3. **Context**: What forces are at play?
4. **Decision**: What are we doing about it?
5. **Consequences**: What becomes easier/harder?
6. **Alternatives Considered**: What else did we evaluate?
7. **Related**: Links to other ADRs, evidence, plans

## 🔢 Numbering Scheme

- **001-099**: Core architecture and foundations
- **100-199**: Module-specific decisions (reserved for future use)
- **future/**: Exploratory ADRs not yet adopted

Current range: 001-024 (+ future ADRs)

## 🔗 Related Documentation

- [Evidence](../evidence/) - Current implementation status
- [Plans](../plan/) - Future work based on these decisions
- [Design](../design/) - Active design work
- [Archive](../archive/) - Historical docs superseded by ADRs

## ⚠️ Important Notes

- **ADRs are immutable**: Don't edit accepted ADRs (create superseding ADR instead)
- **Be specific**: Include code examples, diagrams, concrete details
- **Explain alternatives**: Document what you considered and why you rejected it
- **Update INDEX.md**: Always keep the index current

## 💡 Tips for Great ADRs

1. **Start with "why"**: Context is more important than the decision itself
2. **Be honest about trade-offs**: Every decision has consequences
3. **Use examples**: Code samples make decisions concrete
4. **Link liberally**: Connect to related documents
5. **Think long-term**: Future you will read this - make it clear!

---

**For the complete ADR index and mappings, see [INDEX.md](INDEX.md)**
