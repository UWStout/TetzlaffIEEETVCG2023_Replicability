package openGL.wrappers.implementations;

import static openGL.OpenGLHelper.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

import openGL.wrappers.interfaces.FramebufferAttachment;

public class OpenGLRenderbuffer implements FramebufferAttachment
{
	private int renderbufferId;
	
	public OpenGLRenderbuffer(int samples, int internalFormat, int width, int height) 
	{
		this.renderbufferId = glGenRenderbuffers();
		openGLErrorCheck();
		this.bind();
		glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, internalFormat, width, height);
		openGLErrorCheck();
	}
	
	@Override
	public void bind()
	{
		glBindRenderbuffer(GL_RENDERBUFFER, this.renderbufferId);
		openGLErrorCheck();
	}

	@Override
	public void attachToDrawFramebuffer(int attachment, int level) 
	{
		if (level != 0)
		{
			throw new IllegalArgumentException("Renderbuffers only have one level to bind to.");
		}
		glFramebufferRenderbuffer(GL_DRAW_FRAMEBUFFER, attachment, GL_RENDERBUFFER, this.renderbufferId);
		openGLErrorCheck();
	}

	@Override
	public void attachToReadFramebuffer(int attachment, int level) 
	{
		if (level != 0)
		{
			throw new IllegalArgumentException("Renderbuffers only have one level to bind to.");
		}
		glFramebufferRenderbuffer(GL_READ_FRAMEBUFFER, attachment, GL_RENDERBUFFER, this.renderbufferId);
		openGLErrorCheck();
	}

	@Override
	public void delete()
	{
		glDeleteRenderbuffers(this.renderbufferId);
		openGLErrorCheck();
	}
}
