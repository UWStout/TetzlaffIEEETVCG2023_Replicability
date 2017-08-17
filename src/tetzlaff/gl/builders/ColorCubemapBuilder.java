package tetzlaff.gl.builders;

import java.io.IOException;

import tetzlaff.gl.*;
import tetzlaff.gl.nativebuffer.NativeVectorBuffer;

public interface ColorCubemapBuilder <ContextType extends Context<ContextType>, TextureType extends Cubemap<ContextType>> 
extends ColorTextureBuilder<ContextType, TextureType>
{
    ColorCubemapBuilder<ContextType, TextureType> loadFace(CubemapFace face, NativeVectorBuffer data) throws IOException;

    @Override
    ColorCubemapBuilder<ContextType, TextureType> setInternalFormat(ColorFormat format);
    @Override
    ColorCubemapBuilder<ContextType, TextureType> setInternalFormat(CompressionFormat format);

    @Override
    ColorCubemapBuilder<ContextType, TextureType> setMultisamples(int samples, boolean fixedSampleLocations);
    @Override
    ColorCubemapBuilder<ContextType, TextureType> setMipmapsEnabled(boolean enabled);
    @Override
    ColorCubemapBuilder<ContextType, TextureType> setLinearFilteringEnabled(boolean enabled);
    @Override
    ColorCubemapBuilder<ContextType, TextureType> setMaxAnisotropy(float maxAnisotropy);
}
