package tetzlaff.gl.window.listeners;

import tetzlaff.gl.window.ModifierKeys;
import tetzlaff.gl.window.Window;

public interface CharacterModifiersListener
{
    void characterTypedWithModifiers(Window<?> window, char c, ModifierKeys mods);
}
