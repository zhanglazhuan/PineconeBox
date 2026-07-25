"""Test inject engine."""

from inject import LAUNCHERS_TO_REMOVE


def test_launchers_list_not_empty():
    assert len(LAUNCHERS_TO_REMOVE) >= 5
    assert "Trebuchet" in LAUNCHERS_TO_REMOVE
    assert "LeanbackLauncher" in LAUNCHERS_TO_REMOVE
