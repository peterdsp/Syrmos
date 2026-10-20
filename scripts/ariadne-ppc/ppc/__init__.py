"""Ariadne Propose-Probe-Compose: a dev-time optimizer for the LLM contract.

Adapted from google-research/harness2. The editable surface is the model-facing
pack under docs/ariadne; the deterministic app core (parser, grounder, dispatch,
bundled facts) stays frozen. See ../README.md for the contract.
"""

__all__ = ["adapter", "harness", "model", "recursion", "gate", "config"]
