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

package tetzlaff.ibrelight.export.specularfit;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import tetzlaff.gl.core.Context;
import tetzlaff.gl.core.Drawable;
import tetzlaff.gl.core.Texture2D;
import tetzlaff.ibrelight.rendering.resources.IBRResources;

public class SpecularFitFromPriorSolution<ContextType extends Context<ContextType>> extends SpecularFitBase<ContextType>
{
    /**
     * Estimated surface normals
     */
    final Texture2D<ContextType> normalMap;

    public SpecularFitFromPriorSolution(ContextType context, IBRResources<ContextType> resources, SpecularFitSettings settings,
        File priorSolutionDirectory) throws IOException
    {
        super(context, resources, settings);

        // Load normal map
        normalMap = context.getTextureFactory()
            .build2DColorTextureFromFile(new File(priorSolutionDirectory, "normal.png"), true)
            .setLinearFilteringEnabled(true)
            .createTexture();

        basisResources.loadFromPriorSolution(priorSolutionDirectory);
    }

    @Override
    public Texture2D<ContextType> getNormalMap()
    {
        return normalMap;
    }
}
