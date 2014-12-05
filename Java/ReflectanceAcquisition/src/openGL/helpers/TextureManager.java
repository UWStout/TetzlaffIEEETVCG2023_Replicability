package openGL.helpers;

import openGL.wrappers.exceptions.NoAvailableTextureUnitsException;
import openGL.wrappers.interfaces.Texture;

public class TextureManager 
{
	public final int length;
	
	private int[] keys;
	private Texture[] textures;
	private int nextSlot;

	public TextureManager(int length) 
	{
		this.length = length;
		keys = new int[length];
		textures = new Texture[length];
		nextSlot = 0;
	}

	public int assignTextureByKey(int key, Texture texture)
	{
		// Check if the key has already been assigned a texture
		for (int i = 0; i < length; i++)
		{
			if (keys[i] == key)
			{
				textures[i] = texture;
				return i;
			}
		}
		
		if (nextSlot == length)
		{
			// No more slots available.
			throw new NoAvailableTextureUnitsException("No more available texture units.");
		}
		else
		{
			// The key has not been assigned a texture, so use the next available slot
			keys[nextSlot] = key;
			textures[nextSlot] = texture;
			return nextSlot++;
		}
	}
	
	public Texture getTextureByUnit(int index)
	{
		return textures[index];
	}
}
