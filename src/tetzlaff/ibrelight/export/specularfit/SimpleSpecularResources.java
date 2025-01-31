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

import tetzlaff.gl.core.Context;
import tetzlaff.gl.core.Texture2D;

public class SimpleSpecularResources<ContextType extends Context<ContextType>> implements SpecularResources<ContextType>
{
    private Texture2D<ContextType> diffuseMap;
    private Texture2D<ContextType> normalMap;
    private Texture2D<ContextType> specularReflectivityMap;
    private Texture2D<ContextType> specularRoughnessMap;
    private BasisResources<ContextType> basisResources;

    @Override
    public Texture2D<ContextType> getDiffuseMap()
    {
        return diffuseMap;
    }

    @Override
    public Texture2D<ContextType> getNormalMap()
    {
        return normalMap;
    }

    @Override
    public Texture2D<ContextType> getSpecularReflectivityMap()
    {
        return specularReflectivityMap;
    }

    @Override
    public Texture2D<ContextType> getSpecularRoughnessMap()
    {
        return specularRoughnessMap;
    }

    @Override
    public BasisResources<ContextType> getBasisResources()
    {
        return basisResources;
    }

    /**
     * Sets the diffuse texture.  This object will take ownership of the diffuse map.
     * @param diffuseMap
     */
    public void setDiffuseMap(Texture2D<ContextType> diffuseMap)
    {
        this.diffuseMap = diffuseMap;
    }

    /**
     * Sets the normal texture.  This object will take ownership of the normal map.
     * @param normalMap
     */
    public void setNormalMap(Texture2D<ContextType> normalMap)
    {
        this.normalMap = normalMap;
    }

    /**
     * Sets the specular reflectivity texture.  This object will take ownership of the specular map.
     * @param specularReflectivityMap
     */
    public void setSpecularReflectivityMap(Texture2D<ContextType> specularReflectivityMap)
    {
        this.specularReflectivityMap = specularReflectivityMap;
    }

    /**
     * Sets the specular roughness texture.  This object will take ownership of the roughness map.
     * @param specularRoughnessMap
     */
    public void setSpecularRoughnessMap(Texture2D<ContextType> specularRoughnessMap)
    {
        this.specularRoughnessMap = specularRoughnessMap;
    }

    /**
     * Sets the basis resources (i.e. weight texture array, basis function 1D texture array, etc.).
     * This object will take ownership of the basis resources.
     * @param basisResources
     */
    public void setBasisResources(BasisResources<ContextType> basisResources)
    {
        this.basisResources = basisResources;
    }

    @Override
    public void close()
    {
        if (diffuseMap != null)
        {
            diffuseMap.close();
        }

        if (normalMap != null)
        {
            normalMap.close();
        }

        if (specularReflectivityMap != null)
        {
            specularReflectivityMap.close();
        }

        if (specularRoughnessMap != null)
        {
            specularRoughnessMap.close();
        }

        if (basisResources != null)
        {
            basisResources.close();
        }
    }
}
