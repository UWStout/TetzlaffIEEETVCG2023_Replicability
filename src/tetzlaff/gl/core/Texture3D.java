/*
 *  Copyright (c) Michael Tetzlaff 2022
 *
 *  Licensed under GPLv3
 *  ( http://www.gnu.org/licenses/gpl-3.0.html )
 *
 *  This code is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 */

package tetzlaff.gl.core;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;

import tetzlaff.gl.nativebuffer.NativeVectorBuffer;
import tetzlaff.gl.types.AbstractDataType;

/**
 * An interface for a three-dimensional texture.
 * @author Michael Tetzlaff
 *
 * @param <ContextType> The type of the GL context that the texture is associated with.
 */
public interface Texture3D<ContextType extends Context<ContextType>> extends Texture<ContextType>
{
    /**
     * Gets the width of the texture.
     * @return The width of the texture.
     */
    int getWidth();

    /**
     * Gets the height of the texture.
     * @return The height of the texture.
     */
    int getHeight();

    /**
     * Gets teh depth of the texture (the number of layers if used as a texture array).
     * @return The depth of the texture.
     */
    int getDepth();

    /**
     * Sets the texture wrap modes.
     * @param wrapS The horizontal wrap mode.
     * @param wrapT The vertical wrap mode.
     * @param wrapR the wrap mode in the depth dimension.
     */
    void setTextureWrap(TextureWrapMode wrapS, TextureWrapMode wrapT, TextureWrapMode wrapR);

    /**}
     * Loads pixel data and sends it to the GPU for a specific layer of the 3D texture, replacing whatever pixel data was there before.
     * @param layerIndex The index of the layer where the pixel data should go.
     * @param fileStream The stream from which to read the texture layer.
     * @param flipVertical Whether or not to automatically flip all of the pixels vertically to resolve discrepancies with respect to the orientation of the vertical axis.
     * @throws IOException Upon a File I/O problem while loading the image.
     */
    void loadLayer(int layerIndex, InputStream fileStream, boolean flipVertical) throws IOException;

    /**
     * Loads pixel data and sends it to the GPU for a specific layer of the 3D texture, replacing whatever pixel data was there before.
     * @param layerIndex The index of the layer where the pixel data should go.
     * @param file A file containing the image in a format supported by Java's ImageIO library.
     * @param flipVertical Whether or not to automatically flip all of the pixels vertically to resolve discrepancies with respect to the orientation of the vertical axis.
     * @throws IOException Upon a File I/O problem while loading the image.
     */
    void loadLayer(int layerIndex, File file, boolean flipVertical) throws IOException;

    /**
     * Loads pixel data and sends it to the GPU for a specific layer of the 3D texture, replacing whatever pixel data was there before.
     * @param layerIndex The index of the layer where the pixel data should go.
     * @param imageStream A stream containing the image in a format supported by Java's ImageIO library.
     * @param maskStream A stream containing the alpha mask in a format supported by Java's ImageIO library.
     * @param flipVertical Whether or not to automatically flip all of the pixels vertically to resolve discrepancies with respect to the orientation of the vertical axis.
     * @throws IOException Upon a File I/O problem while loading the image.
     */
    void loadLayer(int layerIndex, InputStream imageStream, InputStream maskStream, boolean flipVertical) throws IOException;

    /**
     * Loads pixel data and sends it to the GPU for a specific layer of the 3D texture, replacing whatever pixel data was there before.
     * @param layerIndex The index of the layer where the pixel data should go.
     * @param imageFile A file containing the image in a format supported by Java's ImageIO library.
     * @param maskFile A file containing the alpha mask in a format supported by Java's ImageIO library.
     * @param flipVertical Whether or not to automatically flip all of the pixels vertically to resolve discrepancies with respect to the orientation of the vertical axis.
     * @throws IOException Upon a File I/O problem while loading the image.
     */
    void loadLayer(int layerIndex, File imageFile, File maskFile, boolean flipVertical) throws IOException;

    /**
     * Loads pixel data and sends it to the GPU for a specific layer of the 3D texture, replacing whatever pixel data was there before.
     * Maps the pixel data to another format before storing it in the texture layer.
     * @param layerIndex The index of the layer where the pixel data should go.
     * @param fileStream A stream containing the image in a format supported by Java's ImageIO library.
     * @param flipVertical Whether or not to automatically flip all of the pixels vertically to resolve discrepancies with respect to the orientation of the vertical axis.
     * @param mappedType The type to which to map the data for storage in the texture.
     * @param mappingFunction The function that transforms the color from the image to the mapped type for storage in the texture.
     * @param <MappedType> The high-level return type of the mapping function.
     *                     This is typically either Number (for single-channel textures) or an Iterable of Numbers for multi-channel textures.
     * @throws IOException Upon a File I/O problem while loading the image.
     */
    <MappedType> void loadLayer(int layerIndex, InputStream fileStream, boolean flipVertical,
        AbstractDataType<? super MappedType> mappedType, Function<Color, MappedType> mappingFunction) throws IOException;

    /**
     * Loads pixel data and sends it to the GPU for a specific layer of the 3D texture, replacing whatever pixel data was there before.
     * Maps the pixel data to another format before storing it in the texture layer.
     * @param layerIndex The index of the layer where the pixel data should go.
     * @param file A file containing the image in a format supported by Java's ImageIO library.
     * @param flipVertical Whether or not to automatically flip all of the pixels vertically to resolve discrepancies with respect to the orientation of the vertical axis.
     * @param mappedType The type to which to map the data for storage in the texture.
     * @param mappingFunction The function that transforms the color from the image to the mapped type for storage in the texture.
     * @param <MappedType> The high-level return type of the mapping function.
     *                     This is typically either Number (for single-channel textures) or an Iterable of Numbers for multi-channel textures.
     * @throws IOException Upon a File I/O problem while loading the image.
     */
    <MappedType> void loadLayer(int layerIndex, File file, boolean flipVertical,
        AbstractDataType<? super MappedType> mappedType, Function<Color, MappedType> mappingFunction) throws IOException;

    /**
     * Loads pixel data and sends it to the GPU for a specific layer of the 3D texture, replacing whatever pixel data was there before.
     * Maps the pixel data to another format before storing it in the texture layer.
     * @param layerIndex The index of the layer where the pixel data should go.
     * @param imageStream A stream containing the image in a format supported by Java's ImageIO library.
     * @param maskStream A stream containing the alpha mask in a format supported by Java's ImageIO library.
     * @param flipVertical Whether or not to automatically flip all of the pixels vertically to resolve discrepancies with respect to the orientation of the vertical axis.
     * @param mappedType The type to which to map the data for storage in the texture.
     * @param mappingFunction The function that transforms the color from the image to the mapped type for storage in the texture.
     * @param <MappedType> The high-level return type of the mapping function.
     *                     This is typically either Number (for single-channel textures) or an Iterable of Numbers for multi-channel textures.
     * @throws IOException Upon a File I/O problem while loading the image.
     */
    <MappedType> void loadLayer(int layerIndex, InputStream imageStream, InputStream maskStream, boolean flipVertical,
        AbstractDataType<? super MappedType> mappedType, Function<Color, MappedType> mappingFunction) throws IOException;

    /**
     * Loads pixel data and sends it to the GPU for a specific layer of the 3D texture, replacing whatever pixel data was there before.
     * Maps the pixel data to another format before storing it in the texture layer.
     * @param layerIndex The index of the layer where the pixel data should go.
     * @param imageFile A file containing the image in a format supported by Java's ImageIO library.
     * @param maskFile A file containing the alpha mask in a format supported by Java's ImageIO library.
     * @param flipVertical Whether or not to automatically flip all of the pixels vertically to resolve discrepancies with respect to the orientation of the vertical axis.
     * @param mappedType The type to which to map the data for storage in the texture.
     * @param mappingFunction The function that transforms the color from the image to the mapped type for storage in the texture.
     * @param <MappedType> The high-level return type of the mapping function.
     *                     This is typically either Number (for single-channel textures) or an Iterable of Numbers for multi-channel textures.
     * @throws IOException Upon a File I/O problem while loading the image.
     */
    <MappedType> void loadLayer(int layerIndex, File imageFile, File maskFile, boolean flipVertical,
        AbstractDataType<? super MappedType> mappedType, Function<Color, MappedType> mappingFunction) throws IOException;

    /**
     * Loads pixel data from a buffer and sends it to the GPU for a specific layer of the 3D texture, replacing whatever pixel data was there before.
     * @param layerIndex The index of the layer where the pixel data should go.
     * @param data The new pixel data to put in the texture.
     */
    void loadLayer(int layerIndex, NativeVectorBuffer data);

    /**
     * Gets a single layer of this texture for use as a framebuffer attachment.
     * @param layerIndex The layer to use as a framebuffer attachment.
     * @return An object encapsulating the use of a layer of this texture as a framebuffer attachment.
     */
    FramebufferAttachment<ContextType> getLayerAsFramebufferAttachment(int layerIndex);
}
