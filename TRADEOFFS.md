<b>Known issues or unhandled cases:</b>
1. Equipment awareness relies on model judgment, not a hard rule. `/api/chatbot` tells the model to ask about cookware only when it decides equipment matters for the answer, rather than enforcing that check in code. This avoids nagging on unrelated questions (e.g. "what can I substitute for buttermilk?"), but for an edge case that doesn't obviously read as equipment-heavy (e.g. a recipe needing a stand mixer), the model could occasionally give a suggestion without asking first.
2. 1. I don't think we are handling certain use cases the best possible way.
3. We made the assumption that the first token response must come within 2s rather than the full response.
4. I was going to add a login screen so that preferences can be stored to a user id on the backend for v2 but ran out of time.
5. I wish I could have added unit testing, a better ui as well.
6. More robust testing on pasting things that should not be.
7. Optimization of cost of model vs accurate info from the backend.
8. Make url configurable so it can be changed easily for emulator vs physical device.
