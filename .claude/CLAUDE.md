## Dos
- When making a decision on tech stack always ask the user before making the final decision. All the decisions regarding the tech stack that needs to be used should be referred from `tech_stack.md`. Tech stack includes things like decision on libraries used to networking, logging, database choices, dependency injection library choices and similar things.
- For user facing app, use the `clean_arch_flutter_developer_context.md` on decision regarding the low level design and directory structure.
- For backend services, use the `clean_arch_backend_developer_context.md` on decision regarding the low level design and directory structure.
- High level architecture details are available in `high_level_architecture.md`.
- When structuring code and config files in the repo, do it in a way so that I can easily move the code for different services and frontend application in different repositories if needed.
- All the code related to user facing app should be inside `../client-app` folder.
- All the code related to backend services should be inside `../backend` folder.
- Refer to `generic_coding_guidelines.md` for generic coding guidelines.


## Dont's