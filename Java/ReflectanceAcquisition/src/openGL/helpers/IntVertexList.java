package openGL.helpers;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class IntVertexList
{
	private IntBuffer buffer;
	
	public final int dimensions;
	public final int count;
	
	public IntVertexList(int dimensions, int count)
	{
		if (dimensions < 0)
		{
			throw new IllegalArgumentException("The number of vertex dimensions cannot be negative.");
		}
		if (dimensions > 4)
		{
			throw new IllegalArgumentException("The number of vertex dimensions cannot be greater than four.");
		}
		if (count < 0)
		{
			throw new IllegalArgumentException("The number of vertices cannot be negative.");
		}
		
		this.dimensions = dimensions;
		this.count = count;
		this.buffer = ByteBuffer.allocateDirect(dimensions * count * 4).asIntBuffer();
	}
	
	public IntVertexList(int dimensions, int count, IntBuffer buffer)
	{
		if (dimensions < 0)
		{
			throw new IllegalArgumentException("The number of vertex dimensions cannot be negative.");
		}
		if (dimensions > 4)
		{
			throw new IllegalArgumentException("The number of vertex dimensions cannot be greater than four.");
		}
		if (count < 0)
		{
			throw new IllegalArgumentException("The number of vertices cannot be negative.");
		}
		if (buffer.capacity() < dimensions * count * 4)
		{
			throw new IllegalArgumentException("Insufficient buffer size - a list of " + count + dimensions +
					"D vertices requires a buffer with a capacity of at least " + dimensions * count + ".");
		}
		
		this.dimensions = dimensions;
		this.count = count;
		this.buffer = buffer;
	}
	
	public IntVertexList(int dimensions, int count, int[] buffer)
	{
		this(dimensions, count);
		this.buffer.put(buffer);
		this.buffer.flip();
	}
	
	public int get(int index, int dimension)
	{
		if (index < 0)
		{
			throw new IndexOutOfBoundsException("Index cannot be negative.");
		}
		if (index > this.count)
		{
			throw new IndexOutOfBoundsException("Index (" + index + ") is greater than the size of the vertex list (" + this.count + ").");
		}		
		if (dimension < 0)
		{
			throw new IndexOutOfBoundsException("Dimension cannot be negative.");
		}
		if (dimension > this.count)
		{
			throw new IndexOutOfBoundsException("Dimension (" + dimension + ") is greater than the dimensions of the vertex list (" + this.dimensions + ").");
		}
		return this.buffer.get(index * this.dimensions + dimension);
	}
	
	public void set(int index, int dimension, int value)
	{
		if (index < 0)
		{
			throw new IndexOutOfBoundsException("Index cannot be negative.");
		}
		if (index > this.count)
		{
			throw new IndexOutOfBoundsException("Index (" + index + ") is greater than the size of the vertex list (" + this.count + ").");
		}		
		if (dimension < 0)
		{
			throw new IndexOutOfBoundsException("Dimension cannot be negative.");
		}
		if (dimension > this.count)
		{
			throw new IndexOutOfBoundsException("Dimension (" + dimension + ") is greater than the dimensions of the vertex list (" + this.dimensions + ").");
		}
		this.buffer.put(index * this.dimensions + dimension, value);
	}
	
	public IntBuffer getBuffer()
	{
		return buffer;
	}
}
