/*
 *  Copyright (c) Michael Tetzlaff 2023
 *
 *  Licensed under GPLv3
 *  ( http://www.gnu.org/licenses/gpl-3.0.html )
 *
 *  This code is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 */

package tetzlaff.interactive;

import tetzlaff.gl.core.Context;
import tetzlaff.ibrelight.core.IBRInstance;
import tetzlaff.ibrelight.core.LoadingMonitor;

/**
 * An interface for an executable that only requires a graphics context (no pre-loaded data)
 * @param <ContextType> The type of the graphics context that the renderer implementation uses.
 */
public interface GraphicsRequest<ContextType extends Context<ContextType>>
{
    /**
     * The entry point for the executable.
     * @param context The graphics context to be used.
     * @param callback A callback that can be fired to update the loading bar.
     *                 If this is unused, an "infinite loading" indicator will be displayed instead.
     * @throws Exception An exception may be thrown by the executable that will be caught and logged by IBRelight.
     */
    void executeRequest(ContextType context, LoadingMonitor callback) throws Exception;
}