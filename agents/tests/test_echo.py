import asyncio

from temporalio.testing import ActivityEnvironment

from applyops_agents.activities import echo
from applyops_agents.models import EchoRequest


def test_echo_returns_message_and_language():
    result = asyncio.run(ActivityEnvironment().run(echo, EchoRequest(message="Hello, ApplyOps")))
    assert result.message == "Hello, ApplyOps"
    assert result.language == "python"
    assert result.worker
