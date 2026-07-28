"""PineCone OS Installer — main entry point + state machine."""

import os
import sys
import pygame
from ui import load_fonts
from screens.welcome import WelcomeScreen
from screens.wifi import WifiScreen
from screens.download import StreamFlashScreen

SCREENS = [
    WelcomeScreen,
    WifiScreen,
    StreamFlashScreen,
]

STEP_TITLES = [
    "准备",
    "网络设置",
    "下载刷入",
]


def main():
    pygame.init()

    W, H = 1280, 720
    surface = None
    drivers = ["kmsdrm", "fbcon", ""]
    last_error = None

    for driver in drivers:
        try:
            if driver:
                os.environ["SDL_VIDEODRIVER"] = driver
            pygame.display.init()
            info = pygame.display.Info()
            W, H = info.current_w, info.current_h
            if W == 0 or H == 0:
                W, H = 1920, 1080
            surface = pygame.display.set_mode((W, H), pygame.FULLSCREEN)
            pygame.mouse.set_visible(False)
            last_error = None
            break
        except pygame.error as e:
            pygame.display.quit()
            last_error = str(e)

    if surface is None:
        raise RuntimeError(
            f"Cannot initialize display. Tried: {drivers}. Last error: {last_error}")

    load_fonts()

    clock = pygame.time.Clock()
    current_idx = 0
    total = len(SCREENS)
    screens = [cls(surface, (W, H), step_index=i, total_steps=total, step_titles=STEP_TITLES)
               for i, cls in enumerate(SCREENS)]
    screens[current_idx].on_enter()
    running = True

    while running:
        dt = clock.tick(30) / 1000.0

        for event in pygame.event.get():
            if event.type == pygame.QUIT:
                running = False
                break

            action = screens[current_idx].handle_event(event)
            screen_action = screens[current_idx].update(0) if event.type == pygame.KEYDOWN else None

            triggered = action or screen_action
            if triggered == "next":
                current_idx += 1
                if current_idx >= len(screens):
                    running = False
                else:
                    screens[current_idx].on_enter()
            elif triggered == "back":
                if current_idx > 0:
                    current_idx -= 1
                    screens[current_idx].on_enter()
            elif triggered == "quit":
                running = False

        update_action = screens[current_idx].update(dt)
        if update_action == "next":
            current_idx += 1
            if current_idx >= len(screens):
                running = False
            else:
                screens[current_idx].on_enter()

        screens[current_idx].draw()
        pygame.display.flip()

    pygame.quit()
    sys.exit(0)


if __name__ == "__main__":
    main()
