## Some generic coding guidelines.
- Always use an interface definition between any two layers in the low-level design.
- For all the operational tools like analytics, logging as well, expose the functionality though an interface so that the underlying solution provider can be updated at will.
- All external third-party providers (identity / auth, push notifications, email, SMS, object storage) must be accessed through an interface — a domain port with a provider-specific adapter — so the provider can be swapped without touching business logic or public APIs. For example, Firebase Auth is used only behind an identity-provider interface; no API, service, interface, or data model exposes Firebase-specific types.
- Always include test cases with the new code written.
- When creating the implementation plan, create the implementation plan in a way that the code generated is reviewable.
