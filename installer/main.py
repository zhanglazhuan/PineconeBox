"""PineCone OS Installer — main entry point + state machine.

Run with --dev for PC development mode (windowed, standard SDL driver).
"""

import os
import sys

DEV_MODE = "--dev" in sys.argv or os.environ.get("PINECONE_DEV") == "1"

if DEV_MODE:
    print("PineCone Installer — DEV MODE (PC)")

import pygame
from config import Color
from ui import load_fonts
from screens.welcome import WelcomeScreen
from screens.wifi import WifiScreen
from screens.stream_flash import StreamFlashScreen
from screens.done import DoneScreen

SCREENS = [
    WelcomeScreen,
    WifiScreen,
    StreamFlashScreen,
    DoneScreen,
]


def main():
    pygame.init()

    # Try display drivers in order: kmsdrm -> fbcon -> auto
    W, H = 1280, 720
    surface = None
    fullscreen = not DEV_MODE

    drivers = ["kmsdrm", "fbcon", ""] if not DEV_MODE else [""]

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
            flags = pygame.FULLSCREEN if fullscreen else 0
            surface = pygame.display.set_mode((W, H), flags)
            if not fullscreen:
                pygame.display.set_caption(
                    "PineCone Installer — DEV" if DEV_MODE else "PineCone Installer")
            if fullscreen:
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
    screens = [cls(surface, (W, H)) for cls in SCREENS]
    screens[current_idx].on_enter()
    running = True

    while running:
        dt = clock.tick(30) / 1000.0

        for event in pygame.event.get():
            if event.type == pygame.QUIT:
                running = False
                break
            if event.type == pygame.KEYDOWN:
                if event.key == pygame.K_ESCAPE:
                    running = False
                    break

                action = screens[current_idx].handle_event(event)
                screen_action = screens[current_idx].update(0)

                triggered = action or screen_action
                if triggered == "next":
                    current_idx += 1
                    if current_idx >= len(screens):
                        running = False
                    else:
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
