package openGL.wrappers.implementations;
import static openGL.OpenGLHelper.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.imageio.ImageIO;

import java.util.AbstractCollection;

import openGL.wrappers.interfaces.Framebuffer;
import openGL.wrappers.interfaces.FramebufferAttachment;

public abstract class OpenGLFramebuffer implements Framebuffer 
{
	private boolean viewportSet;
	private int x;
	private int y;
	private int width;
	private int height;
	
	public static Framebuffer defaultFramebuffer()
	{
		return OpenGLDefaultFramebuffer.getInstance();
	}
	
	protected abstract int getId();
	
	@Override
	public int getViewportX()
	{
		return x;
	}
	
	
	@Override
	public int getViewportY()
	{
		return y;
	}
	
	
	@Override
	public int getViewportWidth()
	{
		return width;
	}
	
	
	@Override
	public int getViewportHeight()
	{
		return height;
	}
	
	
	@Override
	public void setViewport(int x, int y, int width, int height)
	{
		if (width < 0 || height < 0)
		{
			throw new IllegalArgumentException("Viewport width and height must be non-negative.");
		}
		this.viewportSet = true;
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		glViewport(x, y, width, height);
		openGLErrorCheck();
	}
	
	@Override
	public void bindForDraw()
	{
		if (viewportSet)
		{
			glBindFramebuffer(GL_DRAW_FRAMEBUFFER, this.getId());
			openGLErrorCheck();
			glViewport(x, y, width, height);
			openGLErrorCheck();
		}
		else
		{
			throw new IllegalStateException("Cannot bind a framebuffer for draw without first calling setViewport().");
		}
	}
	
	protected abstract void selectColorSourceForRead(int index);
	
	@Override
	public void bindForRead(int attachmentIndex)
	{
		if (viewportSet)
		{
			glBindFramebuffer(GL_READ_FRAMEBUFFER, this.getId());
			openGLErrorCheck();
			selectColorSourceForRead(attachmentIndex);
		}
		else
		{
			throw new IllegalStateException("Cannot bind a framebuffer for read without first calling setViewport().");
		}
	}
	
	@Override
	public int[] readPixelsRGBA(int attachmentIndex)
	{
		this.bindForRead(attachmentIndex);
		ByteBuffer pixelBuffer = ByteBuffer.allocateDirect(this.width * this.height * 4);
		glReadPixels(this.x, this.y, this.width, this.height, GL_RGBA, GL_UNSIGNED_BYTE, pixelBuffer);
		openGLErrorCheck();
		int[] pixelArray = new int[this.width * this.height];
		for (int i = 0; i < pixelArray.length; i++) { pixelArray[i] = 0xFF0000FF; }
		pixelBuffer.asIntBuffer().get(pixelArray);
		return pixelArray;
	}
	
	@Override
	public void saveToFile(int attachmentIndex, String fileFormat, String filename) throws IOException
	{
        int[] pixels = this.readPixelsRGBA(attachmentIndex);
        for (int i = 0; i < pixels.length; i++)
        {
        	// Switch from RGBA to ARGB
        	if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN)
        	{
        		pixels[i] = (pixels[i] << 8) | (pixels[i] >>> 24);
        	}
        	else
        	{
        		pixels[i] = (pixels[i] >>> 8) | (pixels[i] << 24);
        	}
        }
        BufferedImage outImg = new BufferedImage(this.getViewportWidth(), this.getViewportHeight(), BufferedImage.TYPE_INT_ARGB);
        outImg.setRGB(0, 0, this.getViewportWidth(), this.getViewportHeight(), pixels, 0, this.getViewportWidth());
        File outputFile = new File(filename);
        ImageIO.write(outImg, fileFormat, outputFile);
	}
}
