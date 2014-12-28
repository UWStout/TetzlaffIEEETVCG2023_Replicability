package tetzlaff.gl.opengl;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static tetzlaff.gl.opengl.helpers.StaticHelpers.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.imageio.ImageIO;

import org.lwjgl.BufferUtils;

import tetzlaff.gl.Framebuffer;

public abstract class OpenGLFramebuffer implements Framebuffer 
{	
	public static OpenGLFramebuffer defaultFramebuffer()
	{
		return OpenGLDefaultFramebuffer.getInstance();
	}
	
	protected abstract int getId();
	
	public abstract int getWidth();
	public abstract int getHeight();

	void bindForDraw(int x, int y, int width, int height)
	{
		glBindFramebuffer(GL_DRAW_FRAMEBUFFER, this.getId());
		openGLErrorCheck();
		glViewport(x, y, width, height);
		openGLErrorCheck();
	}
	
	void bindForDraw()
	{
		this.bindForDraw(0, 0, this.getWidth(), this.getHeight());
	}
	
	protected abstract void selectColorSourceForRead(int index);
	
	protected void bindForRead(int attachmentIndex)
	{
		glBindFramebuffer(GL_READ_FRAMEBUFFER, this.getId());
		openGLErrorCheck();
		selectColorSourceForRead(attachmentIndex);
	}
	
	@Override
	public int[] readColorBufferARGB(int attachmentIndex, int x, int y, int width, int height)
	{
		this.bindForRead(attachmentIndex);
		ByteBuffer pixelBuffer = BufferUtils.createByteBuffer(width * height * 4);
		
		// use BGRA because due to byte order differences it ends up being ARGB
		glReadPixels(x, y, width, height, GL_BGRA, GL_UNSIGNED_BYTE, pixelBuffer);
		openGLErrorCheck();
		
		int[] pixelArray = new int[width * height];
		pixelBuffer.asIntBuffer().get(pixelArray);
		return pixelArray;
	}

	@Override
	public int[] readColorBufferARGB(int attachmentIndex)
	{
		return this.readColorBufferARGB(attachmentIndex, 0, 0, this.getWidth(), this.getHeight());
	}
	
	@Override
	public void saveColorBufferToFile(int attachmentIndex, String fileFormat, String filename) throws IOException
	{
        int[] pixels = this.readColorBufferARGB(attachmentIndex);
        
        // Flip the array vertically
        int height = this.getHeight();
        int width = this.getWidth();
        for (int y = 0; y < height / 2; y++)
        {
        	int limit = (y + 1) * width;
        	for (int i1 = y * width, i2 = (height - y - 1) * width; i1 < limit; i1++, i2++)
        	{
            	int tmp = pixels[i1];
            	pixels[i1] = pixels[i2];
            	pixels[i2] = tmp;
        	}
        }
        
        BufferedImage outImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        outImg.setRGB(0, 0, width, height, pixels, 0, this.getWidth());
        File outputFile = new File(filename);
        ImageIO.write(outImg, fileFormat, outputFile);
	}
	
	@Override
	public void clearColorBuffer(int attachmentIndex, float r, float g, float b, float a)
	{
		glBindFramebuffer(GL_DRAW_FRAMEBUFFER, this.getId());
		openGLErrorCheck();
		FloatBuffer buffer = BufferUtils.createFloatBuffer(4);
		buffer.put(r);
		buffer.put(g);
		buffer.put(b);
		buffer.put(a);
		buffer.flip();
		glClearBuffer(GL_COLOR, attachmentIndex, buffer);
		openGLErrorCheck();
	}
	
	@Override
	public void clearDepthBuffer(float depth)
	{
		glBindFramebuffer(GL_DRAW_FRAMEBUFFER, this.getId());
		openGLErrorCheck();
		FloatBuffer buffer = BufferUtils.createFloatBuffer(1);
		buffer.put(depth);
		buffer.flip();
		glClearBuffer(GL_DEPTH, 0, buffer);
		openGLErrorCheck();
	}
	
	@Override
	public void clearDepthBuffer()
	{
		this.clearDepthBuffer(1.0f);
	}
	
	@Override
	public void clearStencilBuffer(int stencilIndex)
	{
		glBindFramebuffer(GL_DRAW_FRAMEBUFFER, this.getId());
		openGLErrorCheck();
		IntBuffer buffer = BufferUtils.createIntBuffer(1);
		buffer.put(stencilIndex);
		buffer.flip();
		glClearBuffer(GL_STENCIL, 0, buffer);
		openGLErrorCheck();
	}
}
