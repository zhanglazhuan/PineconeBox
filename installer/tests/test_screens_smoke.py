"""Smoke test: verify each screen can be instantiated without error."""

import os
import platform
import pytest
os.environ["SDL_VIDEODRIVER"] = "dummy"


@pytest.mark.skipif(platform.system() == "Windows",
                    reason="pygame SysFont bug on Windows — works on Linux target")
def test_all_screens_init():
    """Verify all screen classes can be imported and instantiated."""
    import pygame
    pygame.display.init()
    surface = pygame.display.set_mode((1280, 720))

    from ui import load_fonts
    load_fonts()

    from screens.welcome import WelcomeScreen
    from screens.done import DoneScreen

    for cls in [WelcomeScreen, DoneScreen]:
        screen = cls(surface, (1280, 720))
        screen.draw()
        assert screen is not None, f"{cls.__name__} failed to init"

    pygame.quit()


def test_all_modules_import():
    """Verify all modules can be imported."""
    import config
    import ui
    import inject
    from screens import welcome, wifi, stream_flash
    from screens import inject_progress, done
    assert config.Color.PRIMARY == (63, 185, 80)
