/*
 *  Copyright (c) Michael Tetzlaff 2021
 *  Copyright (c) The Regents of the University of Minnesota 2019
 *
 *  Licensed under GPLv3
 *  ( http://www.gnu.org/licenses/gpl-3.0.html )
 *
 *  This code is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 */

package tetzlaff.ibrelight.export.specularfit;

import tetzlaff.gl.core.Context;
import tetzlaff.gl.core.Drawable;
import tetzlaff.gl.core.Framebuffer;
import tetzlaff.gl.core.PrimitiveMode;

public class ShaderBasedErrorCalculator
{
    private double previousError = Double.POSITIVE_INFINITY;
    private double error = Double.POSITIVE_INFINITY;
    private final int texelCount;

    public ShaderBasedErrorCalculator(int texelCount)
    {
        this.texelCount = texelCount;
    }

    public double getPreviousError()
    {
        return previousError;
    }

    public double getError()
    {
        return error;
    }

    public <ContextType extends Context<ContextType>> void update(Drawable<ContextType> drawable, Framebuffer<ContextType> framebuffer)
    {
        // Current error will become the previous error.
        previousError = error;

        // Clear framebuffer
        framebuffer.clearDepthBuffer();
        framebuffer.clearColorBuffer(0, 0.0f, 0.0f, 0.0f, 0.0f);

        // Run shader program to fill framebuffer with per-pixel error.
        drawable.draw(PrimitiveMode.TRIANGLES, framebuffer);

        // Copy framebuffer from GPU to main memory.
        float[] pixelErrors = framebuffer.readFloatingPointColorBufferRGBA(0);

        double errorTotal = 0.0;
        int validCount = 0;

        // Add up per-pixel error.
        for (int p = 0; p < texelCount; p++)
        {
            if (pixelErrors[4 * p + 3] > 0)
            {
                errorTotal += pixelErrors[4 * p];
                validCount += pixelErrors[4 * p + 3];
            }
        }

        error = Math.sqrt(errorTotal / validCount);
    }

    public void reject()
    {
        // Roll back to previous error calculation.
        error = previousError;
    }
}
