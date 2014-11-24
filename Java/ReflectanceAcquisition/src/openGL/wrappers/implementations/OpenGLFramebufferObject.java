package openGL.wrappers.implementations;

import static openGL.OpenGLHelper.openGLErrorCheck;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

import java.util.AbstractCollection;
import java.util.ArrayList;

import openGL.exceptions.OpenGLInvalidFramebufferOperationException;
import openGL.wrappers.interfaces.FramebufferAttachment;
import openGL.wrappers.interfaces.FramebufferObject;

public class OpenGLFramebufferObject extends OpenGLFramebuffer implements FramebufferObject
{
	private int nativeWidth;
	private int nativeHeight;
	private int multisamples;
	private int fboId;
	private AbstractCollection<FramebufferAttachment> attachments;
	
	public OpenGLFramebufferObject(int width, int height, int multisamples, int colorAttachments, boolean depthAttachment, boolean stencilAttachment, boolean combineDepthAndStencil)
	{
		this.fboId = glGenFramebuffers();
		openGLErrorCheck();
		
		this.nativeWidth = width;
		this.nativeHeight = height;
		this.multisamples = multisamples;
		this.attachments = new ArrayList<FramebufferAttachment>();
		
		if (colorAttachments < 0)
		{
			throw new IllegalArgumentException("The number of color attachments cannot be negative.");
		}
		
		if (colorAttachments > GL_MAX_COLOR_ATTACHMENTS)
		{
			throw new IllegalArgumentException("Too many color attachments specified - maximum is " + GL_MAX_COLOR_ATTACHMENTS + ".");
		}
		
		if (colorAttachments == 0 && !depthAttachment && !stencilAttachment)
		{
			throw new IllegalArgumentException("No attachments specified - every FBO must have at least one attachment.");
		}
		
		for (int i = 0; i < colorAttachments; i++)
		{
			addAttachment(GL_COLOR_ATTACHMENT0 + i, GL_RGBA);
		}
		
		if (depthAttachment && stencilAttachment && combineDepthAndStencil)
		{
			addAttachment(GL_DEPTH_STENCIL_ATTACHMENT, GL_DEPTH_STENCIL);
		}
		else
		{
			if (depthAttachment)
			{
				addAttachment(GL_DEPTH_ATTACHMENT, GL_DEPTH_COMPONENT);
			}
			
			if (stencilAttachment)
			{
				addAttachment(GL_STENCIL_ATTACHMENT, GL_STENCIL_INDEX);
			}
		}
		
		if(glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
		{
			throw new OpenGLInvalidFramebufferOperationException();
		}
		openGLErrorCheck();
		
		this.setViewport(0, 0, this.nativeWidth, this.nativeHeight);
	}
	
	private void addAttachment(int attachmentType, int internalFormat)
	{
		FramebufferAttachment attachment = new OpenGLRenderbuffer(this.multisamples, internalFormat, this.nativeWidth, this.nativeHeight);
		glBindFramebuffer(GL_DRAW_FRAMEBUFFER, this.fboId);
		openGLErrorCheck();
		attachment.attachToDrawFramebuffer(attachmentType, 0);
		attachments.add(attachment);
	}
	
	public OpenGLFramebufferObject(int width, int height, int multisamples, int colorAttachments)
	{
		this(width, height, multisamples, colorAttachments, false, false, false);
	}
	
	public OpenGLFramebufferObject(int width, int height, int multisamples)
	{
		this(width, height, multisamples, 1);
	}
	
	public OpenGLFramebufferObject(int width, int height)
	{
		this(width, height, 0);
	}
	
	@Override
	protected int getId()
	{
		return fboId;
	}

	@Override
	protected void selectColorSourceForRead(int index) 
	{
		glReadBuffer(GL_COLOR_ATTACHMENT0 + index);
		openGLErrorCheck();
	}
	
	@Override
	public void delete()
	{
		glDeleteFramebuffers(this.fboId);
		openGLErrorCheck();
		for (FramebufferAttachment attachment : attachments)
		{
			attachment.delete();
		}
	}
}
