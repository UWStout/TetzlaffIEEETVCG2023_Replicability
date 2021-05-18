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

import tetzlaff.gl.core.*;
import tetzlaff.gl.nativebuffer.NativeDataType;
import tetzlaff.gl.nativebuffer.NativeVectorBuffer;
import tetzlaff.gl.nativebuffer.NativeVectorBufferFactory;

@SuppressWarnings("PublicField")
public class SpecularFitResources <ContextType extends Context<ContextType>> implements AutoCloseable
{
    public final Texture3D<ContextType> weightMaps;
    public final Texture2D<ContextType> weightMask;
    public final Texture2D<ContextType> basisMaps;
    public final UniformBuffer<ContextType> diffuseUniformBuffer;
    public final SpecularFitSettings settings;

    public SpecularFitResources(ContextType context, SpecularFitSettings settings)
    {
        this.settings = settings;

        weightMaps = context.getTextureFactory().build2DColorTextureArray(settings.width, settings.height, settings.basisCount)
            .setInternalFormat(ColorFormat.R32F)
            .setLinearFilteringEnabled(true)
            .setMipmapsEnabled(false)
            .createTexture();

        weightMask = context.getTextureFactory().build2DColorTexture(settings.width, settings.height)
            .setInternalFormat(ColorFormat.R8)
            .setLinearFilteringEnabled(true)
            .setMipmapsEnabled(false)
            .createTexture();

        basisMaps = context.getTextureFactory().build1DColorTextureArray(
            settings.microfacetDistributionResolution + 1, settings.basisCount)
            .setInternalFormat(ColorFormat.RGB32F)
            .setLinearFilteringEnabled(true)
            .setMipmapsEnabled(false)
            .createTexture();

        diffuseUniformBuffer = context.createUniformBuffer();

        weightMaps.setTextureWrap(TextureWrapMode.None, TextureWrapMode.None, TextureWrapMode.None);
        basisMaps.setTextureWrap(TextureWrapMode.None, TextureWrapMode.None);

    }

    public void updateFromSolution(SpecularFitSolution solution)
    {
        NativeVectorBufferFactory factory = NativeVectorBufferFactory.getInstance();
        NativeVectorBuffer weightMapBuffer = factory.createEmpty(NativeDataType.FLOAT, 1, settings.width * settings.height);
        NativeVectorBuffer basisMapBuffer = factory.createEmpty(NativeDataType.FLOAT, 3, settings.basisCount * (settings.microfacetDistributionResolution + 1));
        NativeVectorBuffer diffuseNativeBuffer = factory.createEmpty(NativeDataType.FLOAT, 4, settings.basisCount);

        // Load weight mask first.
        for (int p = 0; p < settings.width * settings.height; p++)
        {
            weightMapBuffer.set(p, 0, solution.areWeightsValid(p) ? 1.0 : 0.0);
        }

        weightMask.load(weightMapBuffer);

        for (int b = 0; b < settings.basisCount; b++)
        {
            // Copy weights from the individual solutions into the weight buffer laid out in texture space to be sent to the GPU.
            for (int p = 0; p < settings.width * settings.height; p++)
            {
                weightMapBuffer.set(p, 0, solution.getWeights(p).get(b));
            }

            // Immediately load the weight map so that we can reuse the local memory buffer.
            weightMaps.loadLayer(b, weightMapBuffer);

            // Copy basis functions by color channel into the basis map buffer that will eventually be sent to the GPU..
            for (int m = 0; m <= settings.microfacetDistributionResolution; m++)
            {
                // Format necessary for OpenGL is essentially transposed from the storage in the solution vectors.
                basisMapBuffer.set(m + (settings.microfacetDistributionResolution + 1) * b, 0, solution.getSpecularRed().get(m, b));
                basisMapBuffer.set(m + (settings.microfacetDistributionResolution + 1) * b, 1, solution.getSpecularGreen().get(m, b));
                basisMapBuffer.set(m + (settings.microfacetDistributionResolution + 1) * b, 2, solution.getSpecularBlue().get(m, b));
            }

            // Store each channel of the diffuse albedo in the local buffer.
            diffuseNativeBuffer.set(b, 0, solution.getDiffuseAlbedo(b).x);
            diffuseNativeBuffer.set(b, 1, solution.getDiffuseAlbedo(b).y);
            diffuseNativeBuffer.set(b, 2, solution.getDiffuseAlbedo(b).z);
            diffuseNativeBuffer.set(b, 3, 1.0f);
        }

        // Send the basis functions to the GPU.
        basisMaps.load(basisMapBuffer);

        // Send the diffuse albedos to the GPU.
        diffuseUniformBuffer.setData(diffuseNativeBuffer);
    }

    public void useWithShaderProgram(Program<ContextType> program)
    {
        program.setTexture("basisFunctions", basisMaps);
        program.setTexture("weightMaps", weightMaps);
        program.setTexture("weightMask", weightMask);
        program.setUniformBuffer("DiffuseColors", diffuseUniformBuffer);
    }

    @Override
    public void close()
    {
        weightMaps.close();
        weightMask.close();
        basisMaps.close();
        diffuseUniformBuffer.close();
    }
}
